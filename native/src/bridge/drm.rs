use crate::bridge::java_types::BridgeError;
use jni::{
    Env,
    objects::{JClass, JString},
    sys::{jint, jlong},
};
use rustix::fs::makedev;
use smithay::backend::drm::DrmNode;

pub fn drm_device_by_path<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> Result<jlong, BridgeError> {
    let path = path.try_to_string(env)?;
    let node = DrmNode::from_path(path)?;
    Ok(node.dev_id() as jlong)
}

pub fn drm_device_by_major_minor<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    major: jint,
    minor: jint,
) -> Result<jlong, BridgeError> {
    let id = makedev(major as u32, minor as u32);
    let node = DrmNode::from_dev_id(id)?;
    Ok(node.dev_id() as jlong)
}
