use crate::{
    DmabufFeedbackData, WLCState,
    bridge::{
        java_types::{
            BridgeError, JBufferTexture, JDmabuf, JDmabufFeedbackData,
            JDmabufFormat, JDmabufPlane, JDmabufTexture,
        },
        utils::with_env,
    },
};
use jni::{
    Env,
    objects::{JObject, JObjectArray},
    refs::Global,
    sys::{jint, jlong},
};
use rustix::fd::AsRawFd;
use smithay::backend::allocator::{
    Buffer, Format, Fourcc, Modifier,
    dmabuf::{Dmabuf, WeakDmabuf},
};
use std::ops::Deref;

pub struct BridgeDmabuf {
    pub handle: WeakDmabuf,
    pub java: Global<JDmabufTexture<'static>>,
}

pub fn dmabuf_feedback_from_java<'local>(
    env: &mut Env<'local>,
    jfeedback: JDmabufFeedbackData<'local>,
) -> Result<Option<DmabufFeedbackData>, BridgeError> {
    if jfeedback.is_null() {
        return Ok(None);
    }

    let device = jfeedback.drm_device(env)? as libc::dev_t;
    let formats = jfeedback.formats(env)?;
    let formats = JObjectArray::<JDmabufFormat>::cast_local(env, formats)?;
    let formats = formats_from_java(env, formats)?;
    Ok(Some(DmabufFeedbackData { device, formats }))
}

fn formats_from_java<'local>(
    env: &mut Env<'local>,
    jformats: JObjectArray<'local, JDmabufFormat<'local>>,
) -> Result<Vec<Format>, BridgeError> {
    let mut formats: Vec<Format> = vec![];
    let len = jformats.len(env)?;
    for idx in 0..len {
        let jformat = jformats.get_element(env, idx)? as JDmabufFormat;
        let code = jformat.code(env)? as u32;
        let code = match Fourcc::try_from(code) {
            Ok(f) => f,
            Err(_) => continue,
        };
        let modifier = jformat.modifier(env)? as u64;
        let modifier = Modifier::from(modifier);

        formats.push(Format { code, modifier });
    }

    Ok(formats)
}

pub fn import_dmabuf(dmabuf: &Dmabuf) -> Result<BridgeDmabuf, ()> {
    with_env(|env| _import_dmabuf(env, dmabuf)).ok_or(())
}

fn _import_dmabuf<'local>(
    env: &mut Env<'local>,
    dmabuf: &Dmabuf,
) -> Result<Option<BridgeDmabuf>, BridgeError> {
    let weak = dmabuf.weak();
    let jdmabuf = dmabuf_to_java(env, dmabuf)?;

    let tex = JBufferTexture::create_dmabuf_texture(env, jdmabuf)?;
    if tex.is_null() {
        return Ok(None);
    }

    let tex = env.new_global_ref(tex)?;

    Ok(Some(BridgeDmabuf {
        handle: weak,
        java: tex,
    }))
}

pub fn free_dmabuf(state: &mut WLCState, dmabuf: &Dmabuf) {
    with_env(|env| _free_dmabuf(env, state, dmabuf))
}

fn _free_dmabuf<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    dmabuf: &Dmabuf,
) -> Result<(), BridgeError> {
    let weak = dmabuf.weak();
    let bridge_dmabuf = state
        .bridge
        .dmabufs
        .extract_if(.., |d| d.handle == weak)
        .next()
        .expect("free_dmabuf");

    bridge_dmabuf.java.free_internal(env)?;
    drop(bridge_dmabuf);

    Ok(())
}

pub fn get_dmabuf_java<'local, 'a>(
    _env: &mut Env<'local>,
    state: &'a mut WLCState,
    dmabuf: &Dmabuf,
) -> Option<&'a JDmabufTexture<'local>> {
    let weak = dmabuf.weak();
    state
        .bridge
        .dmabufs
        .iter()
        .filter(|d| d.handle == weak)
        .next()
        .map(|d| d.java.deref())
}

fn dmabuf_to_java<'local>(
    env: &mut Env<'local>,
    dmabuf: &Dmabuf,
) -> Result<JDmabuf<'local>, BridgeError> {
    let array = JObjectArray::<JDmabufPlane>::new(
        env,
        dmabuf.num_planes(),
        JDmabufPlane::null(),
    )?;

    let mut handles = dmabuf.handles();
    let mut offsets = dmabuf.offsets();
    let mut strides = dmabuf.strides();

    for idx in 0..dmabuf.num_planes() {
        let handle = handles.next().unwrap().as_raw_fd();
        let offset = offsets.next().unwrap();
        let stride = strides.next().unwrap();
        let plane =
            JDmabufPlane::new(env, handle, offset as jint, stride as jint)?;
        array.set_element(env, idx, plane)?;
    }

    let array = JObjectArray::<JObject>::cast_local(env, array)?;
    let jdmabuf = JDmabuf::new(
        env,
        dmabuf.width() as jint,
        dmabuf.height() as jint,
        (dmabuf.format().code as u32) as jint,
        u64::from(dmabuf.format().modifier) as jlong,
        array,
    )?;

    Ok(jdmabuf)
}
