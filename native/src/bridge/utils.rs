use crate::bridge::java_types::BridgeError;
use jni::{
    Env, vm::JavaVM,
};

pub fn with_env<T, F>(
    callback: F
) -> T
    where F: FnOnce(&mut Env) -> Result<T, BridgeError>
{
    let result = JavaVM::singleton()
        .unwrap()
        .attach_current_thread(callback);
    match result {
        Ok(t) => t,
        Err(e) => {
            panic!("ERROR: {}", e);
        },
    }
}


#[macro_export]
macro_rules! jptr_to_instance {
    ($jptr:expr) => {
        if $jptr == 0 {
            Err($crate::bridge::java_types::BridgeError::NullInstancePtr)
        } else {
            Ok(unsafe { &mut *(($jptr as usize) as *mut $crate::WaylandCraft) })
        }
    };
}
pub use jptr_to_instance;
