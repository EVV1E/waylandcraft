use crate::{
    WLCState,
    bridge::{
        compositor::{get_java_surface_opt, surface_from_java_nullable},
        java_types::{BridgeError, WLCSurface},
        utils::{jptr_to_instance, with_env},
    },
};
use jni::{
    Env,
    objects::{JClass},
    sys::{jdouble, jint, jlong},
};

pub fn dnd_cancel<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    instance.state.data.dnd_cancel();

    Ok(())
}

pub fn dnd_drop<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    instance.state.data.dnd_drop();

    Ok(())
}

pub fn dnd_motion<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    jsurface: WLCSurface<'local>,
    x: jdouble,
    y: jdouble,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let surface = surface_from_java_nullable(env, &jsurface)?;
    instance.state.data.dnd_motion(surface.as_ref(), x, y);

    Ok(())
}

pub fn dnd_icon<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<WLCSurface<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    if let Some(ref dnd) = instance.state.data.dnd {
        let jsurface = get_java_surface_opt!(&dnd.icon);
        let jsurface_ref = env.new_local_ref(jsurface)?;
        Ok(jsurface_ref)
    } else {
        Ok(WLCSurface::null())
    }
}

pub fn start_dnd(
    state: &mut WLCState,
    serial: u32,
) {
    with_env(|env| _start_dnd(env, state, serial))
}

pub fn _start_dnd<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    serial: u32,
) -> Result<(), BridgeError> {
    state.bridge.java.call_on_dnd(env, serial as jint)?;

    Ok(())
}
