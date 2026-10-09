use crate::bridge::{java_types::BridgeError, utils::jptr_to_instance};
use jni::{
    Env,
    objects::{JClass, JIntArray, JPrimitiveArray},
    sys::{jint, jlong},
};
use smithay::{
    reexports::wayland_protocols::xdg::shell::server::xdg_toplevel, utils::Size,
};

pub fn output_size<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let instance = jptr_to_instance!(instance)?;

    let size = instance.state.output.size();
    let size: [jint; 2] = [size.w, size.h];

    let array = JIntArray::new(env, 2)?;
    array.set_region(env, 0, &size)?;
    Ok(array)
}

pub fn output_bounds<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let instance = jptr_to_instance!(instance)?;

    let bounds = instance.state.output.bounds();
    let bounds: [jint; 2] = [bounds.w, bounds.h];

    let array = JIntArray::new(env, 2)?;
    array.set_region(env, 0, &bounds)?;
    Ok(array)
}

pub fn output_resize<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    width: jint,
    height: jint,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let size = instance.state.output.size();
    let width_changed = size.w != width;
    let height_changed = size.h != height;

    if width < 1 || height < 1 {
        return Err(BridgeError::InvalidOutputSize);
    }

    if !width_changed && !height_changed {
        return Ok(());
    }

    instance.state.output.resize(width, height);

    for toplevel in instance.state.xdg_state.toplevel_surfaces() {
        toplevel.with_pending_state(|state| {
            if state.states.contains(xdg_toplevel::State::Fullscreen) {
                state.size = Some(Size::new(width, height));
            }
        });

        toplevel.send_pending_configure();
    }

    Ok(())
}

pub fn output_set_bounds<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    width: jint,
    height: jint,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let bounds = instance.state.output.bounds();
    let width_changed = bounds.w != width;
    let height_changed = bounds.h != height;

    if width < 1 || height < 1 {
        return Err(BridgeError::InvalidOutputSize);
    }

    if !width_changed && !height_changed {
        return Ok(());
    }

    instance.state.output.set_bounds(width, height);

    for toplevel in instance.state.xdg_state.toplevel_surfaces() {
        toplevel.with_pending_state(|state| {
            if state.states.contains(xdg_toplevel::State::Maximized) {
                state.size = Some(Size::new(width, height));
            }
        });

        toplevel.send_pending_configure();
    }

    Ok(())
}
