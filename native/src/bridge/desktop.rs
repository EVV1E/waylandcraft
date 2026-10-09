use crate::{
    bridge::{
        java_types::{BridgeError, JRawDesktopEntry},
        utils::jptr_to_instance,
    },
    desktop::RawDesktopEntry,
};
use jni::{
    Env,
    objects::{JClass, JObjectArray, JString},
    sys::{jboolean, jint, jlong},
};
use std::path::PathBuf;

fn raw_desktop_entry_to_java<'local>(
    env: &mut Env<'local>,
    entry: &RawDesktopEntry,
) -> Result<JRawDesktopEntry<'local>, BridgeError> {
    macro_rules! opt_to_jstring {
        ($env:expr, $string:expr) => {
            match $string {
                Some(string) => JString::new($env, string),
                None => Ok(JString::null()),
            }
        };
    }

    let app_id = JString::new(env, &entry.app_id)?;
    let name = opt_to_jstring!(env, &entry.name)?;
    let generic_name = opt_to_jstring!(env, &entry.generic_name)?;
    let exec = opt_to_jstring!(env, &entry.exec)?;
    let exec_terminal = entry.exec_terminal;
    let comment = opt_to_jstring!(env, &entry.comment)?;
    let visible = entry.visible;
    let icon_path = opt_to_jstring!(env, &entry.icon_path)?;

    let keywords = entry
        .keywords
        .iter()
        .map(|keyword| JString::new(env, keyword))
        .collect::<Result<Vec<_>, _>>()?;

    let kw_array =
        JObjectArray::<JString>::new(env, keywords.len(), &JString::null())?;

    for (index, keyword) in keywords.iter().enumerate() {
        kw_array.set_element(env, index, keyword)?;
    }

    let categories = entry
        .categories
        .iter()
        .map(|keyword| JString::new(env, keyword))
        .collect::<Result<Vec<_>, _>>()?;

    let cat_array =
        JObjectArray::<JString>::new(env, categories.len(), &JString::null())?;

    for (index, category) in categories.iter().enumerate() {
        cat_array.set_element(env, index, category)?;
    }

    Ok(JRawDesktopEntry::new(
        env,
        app_id,
        name,
        generic_name,
        exec,
        exec_terminal,
        comment,
        kw_array,
        cat_array,
        visible,
        icon_path,
    )?)
}

pub fn load_desktop_entry<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    path: JString<'local>,
) -> Result<JRawDesktopEntry<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let path: PathBuf = path.try_to_string(env)?.into();
    let entry = match instance.desktop_helper.load_entry(path) {
        Some(e) => e,
        None => return Ok(JRawDesktopEntry::null()),
    };

    raw_desktop_entry_to_java(env, &entry)
}

pub fn load_desktop_entries<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JObjectArray<'local, JRawDesktopEntry<'local>>, BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let entries = instance.desktop_helper.get_raw_entries();
    let entries = entries
        .iter()
        .map(|e| raw_desktop_entry_to_java(env, e))
        .collect::<Result<Vec<_>, _>>()?;

    let array = JObjectArray::<JRawDesktopEntry>::new(
        env,
        entries.len(),
        &JRawDesktopEntry::null(),
    )?;
    for (index, entry) in entries.iter().enumerate() {
        array.set_element(env, index, entry)?;
    }

    Ok(array)
}

pub fn render_svg<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
    width: jint,
    height: jint,
    buffer_ptr: jlong,
) -> Result<jboolean, BridgeError> {
    let path: PathBuf = path.try_to_string(env)?.into();
    let data = (buffer_ptr as usize) as *mut u8;
    let width = width as u32;
    let height = height as u32;

    Ok(crate::svg::render_svg(path, width, height, data).is_some())
}

pub fn exec_app<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    app_id: JString<'local>,
) -> Result<jboolean, BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let app_id = app_id.try_to_string(env)?;

    let mut env_vars = vec![
        ("WAYLAND_DISPLAY".into(), instance.state.socket.clone()),
        ("QT_QPA_PLATFORM".into(), "wayland".into()),
        ("ELECTRON_OZONE_PLATFORM_HINT".into(), "auto".into()),
        ("GDK_BACKEND".into(), "wayland".into()),
    ];
    if let Some(ref s) = instance.state.satellite {
        env_vars.push(("DISPLAY".into(), s.get_display().into()));
    }

    Ok(instance.desktop_helper.exec_app(app_id, env_vars))
}

pub fn set_preferred_terminal<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    cmd: JString<'local>,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let cmd = cmd.try_to_string(env)?;

    instance.desktop_helper.set_preferred_terminal(cmd);

    Ok(())
}
