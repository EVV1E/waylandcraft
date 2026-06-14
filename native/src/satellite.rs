use std::ffi::OsString;
use std::process::{Command, Child, Stdio};
use rustix::io::Errno;
use rustix::fs::{Access, OFlags, access, lstat, mkdir, open, unlink};
use rustix::process::{Pid, getuid, test_kill_process};
use thiserror::Error;

/* Small parts of this code have been adapted from niri's implementation of
 * xwayland-satellite integration, as well as from Mutter's XWayland code
 * (which niri also borrowed from).
 *
 * niri (https://github.com/niri-wm/niri) is GPLv3 software.
 * Mutter (https://gitlab.gnome.org/GNOME/mutter) is GPLv2-or-later software.
 */

pub struct SatelliteState {
    lock_guard: PathGuard,
}

#[derive(Error, Debug)]
pub enum SatelliteError {
    #[error("Failed to execute xwayland-satellite command: {0}")]
    FailExecute(std::io::Error),
    #[error("xwayland-satellite was unexpectedly terminated by a signal")]
    Terminated,
    #[error("xwayland-satellite does not support -listenfd. Exit status: {0}")]
    NoListenFD(i32),
    #[error("Failed to create X11 directory. Error: {0}")]
    X11DirCreate(Errno),
    #[error("Failed checking tmp directory permissions. Error: {0}")]
    FailTmpDirPermCheck(Errno),
    #[error("Failed checking X11 directory permissions. Error: {0}")]
    FailX11DirPermCheck(Errno),
    #[error("X11 unix directory has the wrong permissions: {0}")]
    X11DirInvalidPerms(&'static str),
    #[error("Failed to create X11 display socket")]
    NoDisplay,
}

struct PathGuard(String);
impl Drop for PathGuard {
    fn drop(&mut self) {
        let _ = unlink(&self.0);
    }
}

const XWS_BINARY: &str = "xwayland-satellite";
const TMP_UNIX_DIR: &str = "/tmp";
const X11_TMP_UNIX_DIR: &str = "/tmp/.X11-unix";

fn test_satellite() -> Result<(), SatelliteError> {
    let mut command = Command::new(XWS_BINARY);
    command
        .arg("--test-listenfd-support")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .env_remove("DISPLAY")
        .env_remove("WAYLAND_DISPLAY")
        .env_remove("LD_LIBRARY_PATH");

    let status = command.status().map_err(SatelliteError::FailExecute)?;
    if status.success() {
        Ok(())
    } else if let Some(code) = status.code() {
        Err(SatelliteError::NoListenFD(code))
    } else {
        Err(SatelliteError::Terminated)
    }
}

// From Mutter (src/wayland/meta-xwayland.c, commit 36ca36b4).
fn ensure_x11_unix_dir() -> Result<(), SatelliteError> {
    match mkdir(X11_TMP_UNIX_DIR, 0o1777.into()) {
        Ok(()) => Ok(()),
        Err(Errno::EXIST) => {
            check_x11_unix_perms()?;
            Ok(())
        }
        Err(err) => Err(SatelliteError::X11DirCreate(err)),
    }
}

// From Mutter (src/wayland/meta-xwayland.c, commit 36ca36b4).
fn check_x11_unix_perms() -> Result<(), SatelliteError> {
    // Query status of the /tmp and /tmp/.X11-unix directories
    let x11_tmp =
        lstat(X11_TMP_UNIX_DIR).map_err(SatelliteError::FailX11DirPermCheck)?;
    let tmp =
        lstat(TMP_UNIX_DIR).map_err(SatelliteError::FailTmpDirPermCheck)?;

    // The owner of the .X11-unix dir should either be the owner of the tmp dir
    // or the current user for security reasons.
    if x11_tmp.st_uid != tmp.st_uid && x11_tmp.st_uid != getuid().as_raw() {
        return Err(SatelliteError::X11DirInvalidPerms("wrong ownership"));
    }

    // The .X11-unix dir has to be writable
    access(X11_TMP_UNIX_DIR, Access::WRITE_OK)
        .map_err(|_| SatelliteError::X11DirInvalidPerms("not writeable"))?;

    // And it should have the sticky bit set
    if (x11_tmp.st_mode & 0o1000) != 0o1000 {
        return Err(SatelliteError::X11DirInvalidPerms("no sticky bit"));
    }

    Ok(())
}

fn maybe_cleanup_lockfile(path: &str) -> Result<(), ()> {
    let data = std::fs::read_to_string(path).map_err(|_| ())?;
    let pid = data.trim().parse::<u32>().map_err(|_| ())?;
    let pid = i32::try_from(pid).map_err(|_| ())?;
    let pid = Pid::from_raw(pid).ok_or(())?;

    if matches!(test_kill_process(pid), Err(Errno::SRCH)) {
        // No process matches the pid in the lockfile, delete it
        let _ = unlink(path);
        return Ok(());
    }

    Ok(())
}

pub fn start_satellite(
    wayland_display: OsString,
) -> Result<SatelliteState, SatelliteError> {
    ensure_x11_unix_dir()?;
    test_satellite()?;

    for dpy in 1..=32 {
        let socket_path = format!("{X11_TMP_UNIX_DIR}/X{dpy}");
        let lock_path = format!("{TMP_UNIX_DIR}/.X{dpy}-lock");

        // Cleanup lockfile if it exists but isn't used anymore
        let _ = maybe_cleanup_lockfile(&lock_path);

        // Create display lock
        let flags =
            OFlags::WRONLY | OFlags::CLOEXEC | OFlags::CREATE | OFlags::EXCL;
        let lock_fd = match open(&lock_path, flags, 0o444.into()) {
            Ok(fd) => fd,
            Err(_) => continue
        };

        // Create guard so the lockfile is deleted when no longer used
        let lock_guard = PathGuard(lock_path);

        println!("Found open display :{dpy}!");

        return Ok(SatelliteState {
            lock_guard,
        });
    }

    Err(SatelliteError::NoDisplay)
}
