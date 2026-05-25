#![allow(dead_code)]

use smithay::backend::{
    allocator::{Buffer, Format, Fourcc, Modifier, dmabuf::Dmabuf},
    drm::{DrmNode, NodeType},
};
use std::ffi::{CStr, CString};
use std::os::fd::AsRawFd;

pub type EGLBoolean = libc::c_uint;
pub type EGLenum = libc::c_uint;
pub type EGLDisplay = *mut libc::c_void;
pub type EGLContext = *mut libc::c_void;
pub type EGLDeviceEXT = *mut libc::c_void;
pub type EGLImage = *mut libc::c_void;
pub type EGLClientBuffer = *mut libc::c_void;
pub type EGLint = i32;
pub type EGLAttrib = libc::intptr_t;
pub type EGLuint64KHR = u64;

pub const EGL_TRUE: EGLBoolean = 1;
pub const EGL_FALSE: EGLBoolean = 0;
pub const EGL_NONE: EGLAttrib = 0x3038;
pub const EGL_NO_CONTEXT: EGLContext = std::ptr::null_mut();
pub const EGL_NO_IMAGE: EGLImage = std::ptr::null_mut();
pub const EGL_WIDTH: EGLAttrib = 0x3057;
pub const EGL_HEIGHT: EGLAttrib = 0x3056;
pub const EGL_LINUX_DMA_BUF_EXT: EGLAttrib = 0x3270;
pub const EGL_LINUX_DRM_FOURCC_EXT: EGLAttrib = 0x3271;
pub const EGL_DEVICE_EXT: EGLint = 0x322C;

pub const EGL_DMA_BUF_PLANE0_FD_EXT: EGLAttrib = 0x3272;
pub const EGL_DMA_BUF_PLANE0_OFFSET_EXT: EGLAttrib = 0x3273;
pub const EGL_DMA_BUF_PLANE0_PITCH_EXT: EGLAttrib = 0x3274;
pub const EGL_DMA_BUF_PLANE1_FD_EXT: EGLAttrib = 0x3275;
pub const EGL_DMA_BUF_PLANE1_OFFSET_EXT: EGLAttrib = 0x3276;
pub const EGL_DMA_BUF_PLANE1_PITCH_EXT: EGLAttrib = 0x3277;
pub const EGL_DMA_BUF_PLANE2_FD_EXT: EGLAttrib = 0x3278;
pub const EGL_DMA_BUF_PLANE2_OFFSET_EXT: EGLAttrib = 0x3279;
pub const EGL_DMA_BUF_PLANE2_PITCH_EXT: EGLAttrib = 0x327A;
pub const EGL_DMA_BUF_PLANE3_FD_EXT: EGLAttrib = 0x3440;
pub const EGL_DMA_BUF_PLANE3_OFFSET_EXT: EGLAttrib = 0x3441;
pub const EGL_DMA_BUF_PLANE3_PITCH_EXT: EGLAttrib = 0x3442;

pub const EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT: EGLAttrib = 0x3443;
pub const EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT: EGLAttrib = 0x3444;
pub const EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT: EGLAttrib = 0x3445;
pub const EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT: EGLAttrib = 0x3446;
pub const EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT: EGLAttrib = 0x3447;
pub const EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT: EGLAttrib = 0x3448;
pub const EGL_DMA_BUF_PLANE3_MODIFIER_LO_EXT: EGLAttrib = 0x3449;
pub const EGL_DMA_BUF_PLANE3_MODIFIER_HI_EXT: EGLAttrib = 0x344A;

pub const EGL_DRM_RENDER_NODE_FILE_EXT: EGLint = 0x3377;
pub const EGL_DRM_DEVICE_FILE_EXT: EGLint = 0x3233;

type ProcAddrFn = extern "C" fn(*const libc::c_char) -> extern "C" fn();

#[allow(non_snake_case)]
pub struct EGLHelper {
    pub display: EGLDisplay,
    eglQueryDisplayAttribEXT:
        extern "C" fn(EGLDisplay, EGLint, *mut EGLAttrib) -> EGLBoolean,
    eglQueryDeviceStringEXT:
        extern "C" fn(EGLDeviceEXT, EGLint) -> *const libc::c_char,
    eglCreateImage: extern "C" fn(
        EGLDisplay,
        EGLContext,
        EGLenum,
        EGLClientBuffer,
        *const EGLAttrib,
    ) -> EGLImage,
    eglGetError: extern "C" fn() -> EGLint,
    eglQueryDmaBufFormatsEXT: extern "C" fn(
        EGLDisplay,
        EGLint,
        *mut EGLint,
        *mut EGLint,
    ) -> EGLBoolean,
    eglQueryDmaBufModifiersEXT: extern "C" fn(
        EGLDisplay,
        EGLint,
        EGLint,
        *mut EGLuint64KHR,
        *mut EGLBoolean,
        *mut EGLint,
    ) -> EGLBoolean,
}

impl EGLHelper {
    #[allow(non_snake_case)]
    #[allow(clippy::missing_transmute_annotations)]
    pub fn new(dpy: EGLDisplay, proc_addr_ptr: usize) -> Self {
        let glfwGetProcAddress: ProcAddrFn =
            unsafe { std::mem::transmute(proc_addr_ptr) };

        macro_rules! getfn {
            ($name:ident) => {
                let $name = {
                    let n = CString::new(stringify!($name)).unwrap();
                    unsafe {
                        std::mem::transmute(glfwGetProcAddress(
                            n.as_c_str().as_ptr(),
                        ))
                    }
                };
            };
        }

        getfn!(eglQueryDisplayAttribEXT);
        getfn!(eglQueryDeviceStringEXT);
        getfn!(eglCreateImage);
        getfn!(eglGetError);
        getfn!(eglQueryDmaBufFormatsEXT);
        getfn!(eglQueryDmaBufModifiersEXT);

        Self {
            display: dpy,
            eglQueryDisplayAttribEXT,
            eglQueryDeviceStringEXT,
            eglCreateImage,
            eglGetError,
            eglQueryDmaBufFormatsEXT,
            eglQueryDmaBufModifiersEXT,
        }
    }

    pub fn get_render_node(&self) -> Result<DrmNode, ()> {
        let mut dev_ret: EGLAttrib = 0;

        if (self.eglQueryDisplayAttribEXT)(
            self.display,
            EGL_DEVICE_EXT,
            &mut dev_ret,
        ) != EGL_TRUE
        {
            eprintln!(
                "Failed to query EGL_DEVICE_EXT! Error: {:x}",
                (self.eglGetError)(),
            );
            return Err(());
        }

        let dev: EGLDeviceEXT = (dev_ret as usize) as EGLDeviceEXT;

        // Try to query the actual render node (e.g. /dev/dri/renderDN)
        let path_ptr =
            (self.eglQueryDeviceStringEXT)(dev, EGL_DRM_RENDER_NODE_FILE_EXT);

        if !path_ptr.is_null() {
            let path = unsafe { CStr::from_ptr(path_ptr).to_str().unwrap() };
            return DrmNode::from_path(path).map_err(|_| ());
        }

        eprintln!(
            "Querying EGL_DRM_RENDER_NODE_FILE_EXT failed! Error: {:x}",
            (self.eglGetError)(),
        );

        // Fall back by getting the EGL DRM device
        let drm_path_ptr =
            (self.eglQueryDeviceStringEXT)(dev, EGL_DRM_DEVICE_FILE_EXT);

        if drm_path_ptr.is_null() {
            eprintln!(
                "Querying EGL_DRM_DEVICE_FILE_EXT failed! Error: {:x}",
                (self.eglGetError)(),
            );
            return Err(());
        }

        let drm_path =
            unsafe { CStr::from_ptr(drm_path_ptr).to_str().unwrap() };

        let drm_device = DrmNode::from_path(drm_path).map_err(|_| ())?;

        // Try to get a new render node
        if let Some(render_node) = drm_device.node_with_type(NodeType::Render) {
            return render_node.map_err(|_| ());
        }

        eprintln!("Failed to get render node from drm device!");

        // If all else fails, just return the drm master node
        Ok(drm_device)
    }

    pub fn dmabuf_to_image(&self, dmabuf: &Dmabuf) -> Result<EGLImage, ()> {
        let mut attribs: Vec<EGLAttrib> = vec![];
        macro_rules! pair {
            ($a:expr, $v:expr) => {
                attribs.push($a as EGLAttrib);
                attribs.push($v as EGLAttrib);
            };
        }

        let mut handles = dmabuf.handles().map(|h| h.as_raw_fd());
        let mut offsets = dmabuf.offsets();
        let mut strides = dmabuf.strides();

        pair!(EGL_WIDTH, dmabuf.width());
        pair!(EGL_HEIGHT, dmabuf.height());
        pair!(EGL_LINUX_DRM_FOURCC_EXT, (dmabuf.format().code as u32));

        let plane_fd_attr = [
            EGL_DMA_BUF_PLANE0_FD_EXT,
            EGL_DMA_BUF_PLANE1_FD_EXT,
            EGL_DMA_BUF_PLANE2_FD_EXT,
            EGL_DMA_BUF_PLANE3_FD_EXT,
        ];
        let plane_offset_attr = [
            EGL_DMA_BUF_PLANE0_OFFSET_EXT,
            EGL_DMA_BUF_PLANE1_OFFSET_EXT,
            EGL_DMA_BUF_PLANE2_OFFSET_EXT,
            EGL_DMA_BUF_PLANE3_OFFSET_EXT,
        ];
        let plane_pitch_attr = [
            EGL_DMA_BUF_PLANE0_PITCH_EXT,
            EGL_DMA_BUF_PLANE1_PITCH_EXT,
            EGL_DMA_BUF_PLANE2_PITCH_EXT,
            EGL_DMA_BUF_PLANE3_PITCH_EXT,
        ];
        let plane_mod_lo_attr = [
            EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT,
            EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT,
            EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT,
            EGL_DMA_BUF_PLANE3_MODIFIER_LO_EXT,
        ];
        let plane_mod_hi_attr = [
            EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT,
            EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT,
            EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT,
            EGL_DMA_BUF_PLANE3_MODIFIER_HI_EXT,
        ];

        for idx in 0..dmabuf.num_planes() {
            pair!(plane_fd_attr[idx], handles.next().unwrap());
            pair!(plane_offset_attr[idx], offsets.next().unwrap());
            pair!(plane_pitch_attr[idx], strides.next().unwrap());

            if dmabuf.has_modifier() {
                let m = u64::from(dmabuf.format().modifier);
                let lo = (m & ((u32::MAX) as u64)) as u32;
                let hi = (m >> 32) as u32;

                pair!(plane_mod_lo_attr[idx], lo);
                pair!(plane_mod_hi_attr[idx], hi);
            }
        }

        attribs.push(EGL_NONE);

        let result = (self.eglCreateImage)(
            self.display,
            EGL_NO_CONTEXT,
            EGL_LINUX_DMA_BUF_EXT as EGLenum,
            std::ptr::null_mut(),
            attribs.as_ptr(),
        );

        match result {
            EGL_NO_IMAGE => Err(()),
            image => Ok(image),
        }
    }

    pub fn query_dmabuf_formats(&self) -> Vec<Format> {
        let codes = self.query_dmabuf_format_codes();
        //println!("Supported dmabuf formats: {:?}", codes);

        let mut formats: Vec<Format> = codes
            .iter()
            .map(|c| Format {
                code: *c,
                modifier: Modifier::Invalid,
            })
            .collect();

        codes.iter().for_each(|&c| {
            let modifiers = self.query_dmabuf_format_modifiers(c);
            //println!("\t{} modifiers: {:?}", c, modifiers);

            let f = modifiers.into_iter().map(|m| Format {
                code: c,
                modifier: m,
            });
            formats.extend(f);
        });

        formats
    }

    fn query_dmabuf_format_codes(&self) -> Vec<Fourcc> {
        // Query amount of formats
        let mut codes_count: EGLint = 0;
        (self.eglQueryDmaBufFormatsEXT)(
            self.display,
            0,
            std::ptr::null_mut(),
            &mut codes_count,
        );

        // Query the format Fourccs
        let mut codes: Vec<EGLint> = Vec::with_capacity(codes_count as usize);
        (self.eglQueryDmaBufFormatsEXT)(
            self.display,
            codes_count,
            codes.as_mut_ptr(),
            &mut codes_count,
        );
        unsafe {
            codes.set_len(codes_count as usize);
        }

        codes
            .iter()
            .filter_map(|&c| Fourcc::try_from(c as u32).ok())
            .collect()
    }

    fn query_dmabuf_format_modifiers(&self, format: Fourcc) -> Vec<Modifier> {
        // Query amount of modifiers
        let mut modifiers_count: EGLint = 0;
        (self.eglQueryDmaBufModifiersEXT)(
            self.display,
            format as EGLint,
            0,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            &mut modifiers_count,
        );

        // Query modifiers
        let mut modifiers: Vec<EGLuint64KHR> =
            Vec::with_capacity(modifiers_count as usize);
        let mut external_only: Vec<EGLBoolean> =
            Vec::with_capacity(modifiers_count as usize);

        (self.eglQueryDmaBufModifiersEXT)(
            self.display,
            format as EGLint,
            modifiers_count,
            modifiers.as_mut_ptr(),
            external_only.as_mut_ptr(),
            &mut modifiers_count,
        );
        unsafe {
            modifiers.set_len(modifiers_count as usize);
            external_only.set_len(modifiers_count as usize);
        }

        let mut external_only = external_only.iter().map(|&b| b == EGL_TRUE);
        // Keep only modifiers that allow non-external access
        modifiers.retain(|_| !external_only.next().unwrap());

        modifiers.into_iter().map(Modifier::from).collect()
    }
}
