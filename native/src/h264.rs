//! Native H.264 encoding with x264 for window sharing (owner side).
//!
//! Output is restricted to what the viewers' pure-Java decoder (JCodec) handles:
//! baseline profile, no B-frames, Annex B with SPS/PPS repeated on every key frame.
//! Key frames only happen when the Java side asks for one (no scene cuts, no key frame
//! interval), because the Java side labels frames as key or delta for the server.
//!
//! Input is RGBA; it is converted to full-range BT.601 I420, matching how the Java side
//! decodes (JCodec's YUV420J).

#![allow(non_snake_case)]

use std::ffi::c_int;
use std::mem::MaybeUninit;
use std::ptr;

use jni::objects::{JByteArray, JClass};
use jni::sys::{jboolean, jint, jlong};
use jni::{Env, bind_java_type};
use thiserror::Error;
use x264_sys::*;

bind_java_type! {
    rust_type = NativeH264Encoder,
    java_type = dev.evvie.waylandcraft.sharing.NativeH264Encoder,

    native_methods {
        static extern fn create {
            sig = (width: jint, height: jint, qp: jint) -> jlong,
            fn = create,
        },
        static extern fn encode {
            sig = (handle: jlong, rgba_ptr: jlong, key_frame: jboolean) -> jbyte[],
            fn = encode,
        },
        static extern fn destroy {
            sig = (handle: jlong),
            fn = destroy,
        },
    },
}

#[derive(Debug, Error)]
enum H264Error {
    #[error(transparent)]
    JniError(#[from] jni::errors::Error),
    #[error("Invalid H.264 encoder size {0}x{1}")]
    InvalidSize(jint, jint),
    #[error("x264 setup failed: {0}")]
    Setup(&'static str),
    #[error("Null H.264 encoder handle")]
    NullHandle,
    #[error("x264 failed to encode a frame")]
    Encode,
}

struct Encoder {
    encoder: *mut x264_t,
    picture: x264_picture_t,
    width: usize,
    height: usize,
    pts: i64,
}

impl Drop for Encoder {
    fn drop(&mut self) {
        unsafe {
            x264_picture_clean(&mut self.picture);
            x264_encoder_close(self.encoder);
        }
    }
}

fn create<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    width: jint,
    height: jint,
    qp: jint,
) -> Result<jlong, H264Error> {
    // 4:2:0 needs even sizes
    if width < 2 || height < 2 || width % 2 != 0 || height % 2 != 0 {
        return Err(H264Error::InvalidSize(width, height));
    }

    unsafe {
        let mut param = MaybeUninit::<x264_param_t>::zeroed();
        if x264_param_default_preset(param.as_mut_ptr(), c"veryfast".as_ptr(), c"zerolatency".as_ptr()) < 0 {
            return Err(H264Error::Setup("preset"));
        }
        let param = param.assume_init_mut();

        param.i_log_level = X264_LOG_NONE as c_int;
        param.i_width = width;
        param.i_height = height;
        param.i_csp = X264_CSP_I420 as c_int;
        param.vui.b_fullrange = 1;
        param.i_fps_num = 15;
        param.i_fps_den = 1;

        // Constant quantizer: steady quality, size follows content
        param.rc.i_rc_method = X264_RC_CQP as c_int;
        param.rc.i_qp_constant = qp;

        // Key frames only on request, each with SPS/PPS so late viewers can start there
        param.i_keyint_max = X264_KEYINT_MAX_INFINITE as c_int;
        param.i_scenecut_threshold = 0;
        param.b_repeat_headers = 1;
        param.b_annexb = 1;

        if x264_param_apply_profile(param, c"baseline".as_ptr()) < 0 {
            return Err(H264Error::Setup("profile"));
        }

        let encoder = x264_encoder_open(param);
        if encoder.is_null() {
            return Err(H264Error::Setup("encoder open"));
        }

        let mut picture = MaybeUninit::<x264_picture_t>::zeroed();
        if x264_picture_alloc(picture.as_mut_ptr(), X264_CSP_I420 as c_int, width, height) < 0 {
            x264_encoder_close(encoder);
            return Err(H264Error::Setup("picture alloc"));
        }

        let encoder = Box::new(Encoder {
            encoder,
            picture: picture.assume_init(),
            width: width as usize,
            height: height as usize,
            pts: 0,
        });
        Ok(Box::into_raw(encoder) as jlong)
    }
}

// rgba_ptr points to width * height tightly packed RGBA pixels, top row first
fn encode<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    handle: jlong,
    rgba_ptr: jlong,
    key_frame: jboolean,
) -> Result<JByteArray<'local>, H264Error> {
    let encoder = unsafe { (handle as *mut Encoder).as_mut() }.ok_or(H264Error::NullHandle)?;
    if rgba_ptr == 0 {
        return Err(H264Error::NullHandle);
    }
    let rgba = unsafe { std::slice::from_raw_parts(rgba_ptr as *const u8, encoder.width * encoder.height * 4) };

    rgba_to_i420(rgba, encoder);

    encoder.picture.i_type = if key_frame { X264_TYPE_IDR } else { X264_TYPE_AUTO } as c_int;
    encoder.picture.i_pts = encoder.pts;
    encoder.pts += 1;

    let mut nals: *mut x264_nal_t = ptr::null_mut();
    let mut nal_count: c_int = 0;
    let mut picture_out = MaybeUninit::<x264_picture_t>::zeroed();
    let size = unsafe {
        x264_encoder_encode(encoder.encoder, &mut nals, &mut nal_count, &mut encoder.picture, picture_out.as_mut_ptr())
    };
    if size < 0 {
        return Err(H264Error::Encode);
    }
    if size == 0 || nals.is_null() {
        // Zero-latency mode outputs every frame immediately; nothing buffered to return
        return Ok(JByteArray::new(env, 0)?);
    }

    // x264 guarantees the NAL payloads of one frame are contiguous
    let data = unsafe { std::slice::from_raw_parts((*nals).p_payload as *const i8, size as usize) };
    let array = JByteArray::new(env, data.len())?;
    array.set_region(env, 0, data)?;
    Ok(array)
}

fn destroy<'local>(_env: &mut Env<'local>, _class: JClass<'local>, handle: jlong) -> Result<(), H264Error> {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Encoder) });
    }
    Ok(())
}

// Full-range BT.601, chroma from the average of each 2x2 block (same as the Java encoder)
fn rgba_to_i420(rgba: &[u8], encoder: &mut Encoder) {
    let (w, h) = (encoder.width, encoder.height);
    let img = &encoder.picture.img;
    let (y_stride, u_stride, v_stride) = (img.i_stride[0] as usize, img.i_stride[1] as usize, img.i_stride[2] as usize);
    let y_plane = unsafe { std::slice::from_raw_parts_mut(img.plane[0], y_stride * h) };
    let u_plane = unsafe { std::slice::from_raw_parts_mut(img.plane[1], u_stride * (h / 2)) };
    let v_plane = unsafe { std::slice::from_raw_parts_mut(img.plane[2], v_stride * (h / 2)) };

    for y in 0..h {
        for x in 0..w {
            let i = (y * w + x) * 4;
            let (r, g, b) = (rgba[i] as i32, rgba[i + 1] as i32, rgba[i + 2] as i32);
            y_plane[y * y_stride + x] = ((77 * r + 150 * g + 29 * b) >> 8) as u8;
        }
    }

    for y in 0..h / 2 {
        for x in 0..w / 2 {
            let (mut r, mut g, mut b) = (0i32, 0i32, 0i32);
            for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)] {
                let i = ((2 * y + dy) * w + 2 * x + dx) * 4;
                r += rgba[i] as i32;
                g += rgba[i + 1] as i32;
                b += rgba[i + 2] as i32;
            }
            let (r, g, b) = (r >> 2, g >> 2, b >> 2);
            u_plane[y * u_stride + x] = (((-43 * r - 85 * g + 128 * b) >> 8) + 128) as u8;
            v_plane[y * v_stride + x] = (((128 * r - 107 * g - 21 * b) >> 8) + 128) as u8;
        }
    }
}
