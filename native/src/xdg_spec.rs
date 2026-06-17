use crate::process::spawn;
use cosmic_freedesktop_icons::lookup;
use freedesktop_desktop_entry::{
    DesktopEntry, desktop_entries, find_app_by_id, get_languages_from_env,
    unicase::Ascii,
};
use std::collections::VecDeque;
use std::ffi::OsString;
use std::path::PathBuf;

pub struct XDGSpecHelper {
    locales: Vec<String>,
    entries: Vec<DesktopEntry>,
    preferred_terminal: String,
}

pub struct RawDesktopEntry {
    pub app_id: String,
    pub name: Option<String>,
    pub generic_name: Option<String>,
    pub exec: Option<String>,
    pub exec_terminal: bool,
    pub comment: Option<String>,
    pub keywords: Vec<String>,
    pub categories: Vec<String>,
    pub visible: bool,
    pub icon_path: Option<String>,
}

impl XDGSpecHelper {
    pub fn init() -> Self {
        let locales = get_languages_from_env();
        let entries = desktop_entries(&locales);

        XDGSpecHelper {
            locales,
            entries,
            preferred_terminal: String::new(),
        }
    }

    pub fn set_preferred_terminal(&mut self, cmd: String) {
        self.preferred_terminal = cmd;
    }

    fn to_raw(&self, entry: &DesktopEntry) -> RawDesktopEntry {
        let icon = self.resolve_icon_path(entry);
        let mut visible = true;
        if entry.hidden() || entry.no_display() {
            visible = false;
        }

        let keywords = entry
            .keywords(&self.locales)
            .unwrap_or_default()
            .iter_mut()
            .map(|k| k.to_mut().clone())
            .collect();

        let categories = entry
            .categories()
            .unwrap_or_default()
            .iter()
            .map(|c| (*c).into())
            .collect();

        RawDesktopEntry {
            app_id: entry.id().into(),
            name: entry.name(&self.locales).map(|c| c.into_owned()),
            generic_name: entry
                .generic_name(&self.locales)
                .map(|c| c.into_owned()),
            exec: entry.exec().map(|s| s.into()),
            exec_terminal: entry.terminal(),
            comment: entry.comment(&self.locales).map(|c| c.into_owned()),
            keywords,
            categories,
            visible,
            icon_path: icon.map(|p| p.into_os_string().into_string().unwrap()),
        }
    }

    pub fn load_entry(&self, path: PathBuf) -> Option<RawDesktopEntry> {
        let entry = DesktopEntry::from_path(path, Some(&self.locales)).ok()?;
        Some(self.to_raw(&entry))
    }

    pub fn get_raw_entries(&self) -> Vec<RawDesktopEntry> {
        self.entries.iter().map(|e| self.to_raw(e)).collect()
    }

    fn resolve_icon_path(&self, entry: &DesktopEntry) -> Option<PathBuf> {
        let icon = entry.icon()?;

        // Absolute icon path
        let abspath = PathBuf::from(icon);
        if abspath.is_absolute() && abspath.is_file() {
            return Some(abspath);
        }

        // Lookup 128x128 icons
        let path = lookup(icon).with_size(128).with_scale(1).find();
        if path.is_some() {
            return path;
        }

        // Lookup 64x64 icons
        let path = lookup(icon).with_size(64).with_scale(1).find();
        if path.is_some() {
            return path;
        }

        // Fallback to any icon paths
        lookup(icon).find()
    }

    pub fn exec_app(
        &self,
        app_id: String,
        env: Vec<(OsString, OsString)>,
    ) -> bool {
        self._exec_app(app_id, env).is_some()
    }

    fn _exec_app(
        &self,
        app_id: String,
        env: Vec<(OsString, OsString)>,
    ) -> Option<()> {
        let entry = find_app_by_id(&self.entries, Ascii::new(&app_id))?;
        let exec = entry.exec()?;
        let mut exec = VecDeque::from(split_exec(exec).ok()?);

        if exec.is_empty() {
            return None;
        }

        if entry.terminal() {
            if self.preferred_terminal.is_empty() { return None }
            exec.push_front("-e".into());
            exec.push_front(self.preferred_terminal.clone());
        }

        // Split off the first element. That's the executable, everything else
        // is arguments.
        let cmd = exec.pop_front().unwrap();
        let args = exec;

        //println!("Executing {cmd} with {:?}", args);

        spawn(cmd, args, env).ok()?;

        Some(())
    }
}

fn split_exec(exec: &str) -> Result<Vec<String>, ()> {
    let parts = shlex::split(exec).ok_or(())?;
    let mut uparts = vec![];
    for part in parts {
        if let Some(p) = unpercent(&part)? {
            uparts.push(p);
        }
    }
    Ok(uparts)
}

// Undo percent sign escaping and check for field codes
// This implementation is strict. A properly formatted argument may either be
// a single field code by itself or a normal argument with properly escaped
// percent signs.
fn unpercent(arg: &str) -> Result<Option<String>, ()> {
    // Check if argument is a field code like %f
    let mut iter = arg.chars();
    if arg.len() >= 2
        && iter.next() == Some('%')
        && iter.next().is_some_and(|c| c.is_ascii_alphabetic())
    {
        if arg.len() > 2 {
            // Not a normal field code if longer than a single percent sign
            // and single ascii alphabetic character.
            return Err(());
        }
        // Correct field code
        return Ok(None);
    }

    // Field codes handled, now assemble output while checking for properly
    // escaped percent signs.
    let mut output = String::new();
    let mut iter = arg.chars();
    while let Some(c) = iter.next() {
        if c == '%' {
            // Next character has to follow, otherwise error
            let c2 = match iter.next() {
                Some(a) => a,
                None => return Err(()),
            };
            if c2 == '%' {
                output.push('%');
            } else {
                return Err(());
            }
        } else {
            // Normal character
            output.push(c);
        }
    }

    Ok(Some(output))
}
