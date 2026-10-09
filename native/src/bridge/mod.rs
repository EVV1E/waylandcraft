use crate::{
    WaylandCraft,
    bridge::{
        dmabuf::{BridgeDmabuf, dmabuf_feedback_from_java},
        java_types::*,
        utils::*,
    },
    wlc_init,
};
use jni::{
    Env,
    objects::{JClass, JString},
    refs::Global,
    sys::jlong,
};
use std::mem::MaybeUninit;
use std::time::Duration;

pub mod compositor;
mod desktop;
pub mod dmabuf;
pub mod dnd;
mod drm;
mod java_types;
mod output;
mod seat;
pub mod shell;
mod utils;

pub struct BridgeState {
    pub java: Global<WaylandCraftBridge<'static>>,
    pub dmabufs: Vec<BridgeDmabuf>,
}

fn init<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    dmabuf_feedback: JDmabufFeedbackData<'local>,
) -> Result<WaylandCraftBridge<'local>, BridgeError> {
    let dmabuf_feedback = dmabuf_feedback_from_java(env, dmabuf_feedback)?;

    // Create memory that holds the instance
    let mut instance_box: Box<MaybeUninit<WaylandCraft>> = Box::new_uninit();
    let ptr = instance_box.as_mut_ptr().addr() as jlong;

    // Call bridge constructor
    // VERY IMPORTANT: The instance pointer is not initialized! The java code
    // MUST NOT perform any bridge calls here!!
    let bridge = WaylandCraftBridge::new(env, ptr)?;
    let bridge_ref = env.new_global_ref(&bridge)?;

    // Create bridge state
    let bridge_state = BridgeState {
        java: bridge_ref,
        dmabufs: vec![],
    };

    let instance =
        wlc_init(bridge_state, dmabuf_feedback).map_err(BridgeError::Init)?;

    // Write instance to the memory allocated earlier
    // After this any calls accessing the state using jptr_to_instance are O.K.
    instance_box.write(instance);

    // Prevent Rust from freeing the allocated memory
    std::mem::forget(instance_box);

    Ok(bridge)
}

fn shutdown<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    // This function acquires the instance from a raw pointer again and
    // drops it. Goes without saying that there shouldn't be any further
    // calls into the bridge after this.

    let ptr = instance as *mut WaylandCraft;
    let _ = unsafe { Box::from_raw(ptr) };

    Ok(())
}

fn dispatch_clients<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    instance
        .event_loop
        .dispatch(Some(Duration::ZERO), &mut instance.state)
        .unwrap();

    Ok(())
}

fn flush_display<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    instance.state.display_handle.flush_clients().unwrap();

    Ok(())
}

fn socket<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JString<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let socket = instance
        .state
        .socket
        .to_str()
        .ok_or(BridgeError::OsStringToUtf8)?;

    Ok(JString::new(env, socket)?)
}

fn x11_display<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JString<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    if let Some(ref s) = instance.state.satellite {
        Ok(JString::new(env, s.get_display())?)
    } else {
        Ok(JString::null())
    }
}
