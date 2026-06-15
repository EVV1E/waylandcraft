#![allow(non_snake_case)]

use crate::egl::{EGLDisplay, EGLHelper};
use crate::java_types::*;
use crate::utils::get_time;
use crate::xdg_spec::RawDesktopEntry;
use crate::{WaylandCraft, wlc_init};
use jni::objects::{JIntArray, JLongArray, JObjectArray, JPrimitiveArray};
use jni::{
    Env, bind_java_type,
    objects::{JClass, JString},
    sys::{jboolean, jbyte, jdouble, jint, jlong},
};
use smithay::{
    backend::allocator::{Buffer, dmabuf::WeakDmabuf},
    reexports::{
        wayland_protocols::xdg::shell::server::xdg_toplevel,
        wayland_server::{
            Resource,
            protocol::{
                wl_buffer::WlBuffer,
                wl_keyboard::KeyState,
                wl_pointer::{Axis, ButtonState},
                wl_surface::WlSurface,
            },
        },
    },
    utils::{Logical, Point, Size},
    wayland::{
        compositor::{
            BufferAssignment, SubsurfaceCachedState, SurfaceAttributes,
            SurfaceData, TraversalAction, with_states, Damage,
            with_states as with_surface_data, with_surface_tree_upward,
        },
        dmabuf::get_dmabuf,
        shell::xdg::{
            PopupSurface, SurfaceCachedState, ToplevelSurface,
            XdgToplevelSurfaceData,
        },
        shm::{self, with_buffer_contents},
        single_pixel_buffer::get_single_pixel_buffer,
        viewporter::{ViewportCachedState, ensure_viewport_valid},
    },
};
use std::ops::DerefMut;
use std::path::PathBuf;
use thiserror::Error;

#[allow(clippy::vec_box)]
pub(crate) struct BridgeState {
    /* Handle collections */
    toplevels: Vec<Box<ToplevelSurface>>,
    popups: Vec<Box<PopupSurface>>,
    surfaces: Vec<Box<WlSurface>>,
    dmabufs: Vec<Box<WeakDmabuf>>,
}

impl BridgeState {
    pub fn new() -> Self {
        BridgeState {
            toplevels: vec![],
            popups: vec![],
            surfaces: vec![],
            dmabufs: vec![],
        }
    }
}

fn jptr_to_ref<T>(ptr: jlong) -> Option<&'static T> {
    let ptr = (ptr as usize) as *const T;
    if ptr.is_null() {
        None
    } else {
        Some(unsafe { &*ptr })
    }
}

fn jptr_to_mut<T>(ptr: jlong) -> Option<&'static mut T> {
    let ptr = (ptr as usize) as *mut T;
    if ptr.is_null() {
        None
    } else {
        Some(unsafe { &mut *ptr })
    }
}

bind_java_type! {
    rust_type = WaylandCraftBridge,
    java_type = dev.evvie.waylandcraft.bridge.WaylandCraftBridge,

    type_map {
        WLCSurface => dev.evvie.waylandcraft.bridge.WLCSurface,
        JRawDesktopEntry => dev.evvie.waylandcraft.desktop.RawDesktopEntry,
    },

    methods {
        fn get_or_create_surface(jlong) -> WLCSurface,
    },

    native_methods {
        static extern fn init {
            sig = (glfw_get_proc_address: jlong, egl_display: jlong) -> jlong,
            fn = init,
        },
        static extern fn shutdown {
            sig = (instance: jlong),
            fn = shutdown,
        },
        static extern fn update {
            sig = (instance: jlong),
            fn = update,
        },
        static extern fn socket {
            sig = (instance: jlong) -> JString,
            fn = socket,
        },
        static extern fn x11_display {
            sig = (instance: jlong) -> JString,
            fn = x11_display,
        },
        static extern fn send_frame {
            sig = (surface_handle: jlong),
            fn = send_frame,
        },
        static extern fn update_surface_data {
            sig = (instance: jlong, surface: WLCSurface),
            fn = update_surface_data,
        },
        static extern fn toplevels {
            sig = (instance: jlong) -> jlong[],
            fn = toplevels,
        },
        static extern fn toplevel_surface {
            sig = (instance: jlong, toplevel_handle: jlong) -> jlong,
            fn = toplevel_surface,
        },
        static extern fn toplevel_title {
            sig = (toplevel_handle: jlong) -> JString,
            fn = toplevel_title,
        },
        static extern fn toplevel_app_id {
            sig = (toplevel_handle: jlong) -> JString,
            name = "toplevelAppID",
            fn = toplevel_app_id,
        },
        static extern fn toplevel_resize {
            sig = (
                toplevel_handle: jlong,
                width: jint,
                height: jint,
                interactive: jboolean
            ),
            fn = toplevel_resize,
        },
        static extern fn toplevel_resize_ovr {
            sig = (toplevel_handle: jlong, width: jint, height: jint),
            fn = toplevel_resize_ovr,
        },
        static extern fn minimize_req {
            sig = (instance: jlong) -> jlong[],
            fn = minimize_req,
        },
        static extern fn maximize_req {
            sig = (instance: jlong) -> jlong[],
            fn = maximize_req,
        },
        static extern fn unmaximize_req {
            sig = (instance: jlong) -> jlong[],
            fn = unmaximize_req,
        },
        static extern fn fullscreen_req {
            sig = (instance: jlong) -> jlong[],
            fn = fullscreen_req,
        },
        static extern fn unfullscreen_req {
            sig = (instance: jlong) -> jlong[],
            fn = unfullscreen_req,
        },
        static extern fn move_request {
            sig = (instance: jlong) -> jint[],
            fn = move_request,
        },
        static extern fn resize_request {
            sig = (instance: jlong) -> jint[],
            fn = resize_request,
        },
        static extern fn fullscreened {
            sig = (instance: jlong) -> jlong[],
            fn = fullscreened,
        },
        static extern fn toplevel_maximize {
            sig = (instance: jlong, toplevel_handle: jlong),
            fn = toplevel_maximize,
        },
        static extern fn toplevel_fullscreen {
            sig = (instance: jlong, toplevel_handle: jlong),
            fn = toplevel_fullscreen,
        },
        static extern fn popups {
            sig = (instance: jlong) -> jlong[],
            fn = popups,
        },
        static extern fn popup_surface {
            sig = (instance: jlong, popup_handle: jlong) -> jlong,
            fn = popup_surface,
        },
        static extern fn popup_parent {
            sig = (instance: jlong, popup_handle: jlong) -> jlong,
            fn = popup_parent,
        },
        static extern fn popup_offset {
            sig = (popup_handle: jlong) -> jint[],
            fn = popup_offset,
        },
        static extern fn surface_xdg_geometry {
            sig = (surface_handle: jlong) -> jint[],
            name = "surfaceXDGGeometry",
            fn = surface_xdg_geometry,
        },
        static extern fn dmabufs {
            sig = (instance: jlong) -> jlong[],
            fn = dmabufs
        },
        extern fn update_surface_tree {
            sig = (instance: jlong, surface: WLCSurface) -> WLCSurface,
            fn = update_surface_tree,
        },
        static extern fn check_input_region {
            sig = (surface_handle: jlong, x: jdouble, y: jdouble) -> jboolean,
            fn = check_input_region,
        },
        static extern fn pointer_motion {
            sig = (instance: jlong, x: jdouble, y: jdouble),
            fn = pointer_motion,
        },
        static extern fn pointer_motion_focus {
            sig = (
                instance: jlong,
                surface_handle: jlong,
                x: jdouble,
                y: jdouble
            ),
            fn = pointer_motion_focus,
        },
        static extern fn pointer_rel_motion {
            sig = (instance: jlong, dx: jdouble, dy: jdouble),
            fn = pointer_rel_motion,
        },
        static extern fn maybe_pointer_lock {
            sig = (instance: jlong, surface_handle: jlong) -> jboolean,
            fn = maybe_pointer_lock,
        },
        static extern fn pointer_unlock {
            sig = (instance: jlong),
            fn = pointer_unlock,
        },
        static extern fn pointer_leave {
            sig = (instance: jlong),
            fn = pointer_leave,
        },
        static extern fn pointer_button {
            sig = (instance: jlong, button: jint, state: jint) -> jint,
            fn = pointer_button,
        },
        static extern fn pointer_axis {
            sig = (instance: jlong, axis: jint, value: jdouble),
            fn = pointer_axis,
        },
        static extern fn cursor_shape {
            sig = (instance: jlong) -> jint,
            fn = cursor_shape,
        },
        static extern fn keyboard_focus {
            sig = (instance: jlong, surface_handle: jlong),
            fn = keyboard_focus,
        },
        static extern fn keyboard_activate {
            sig = (instance: jlong),
            fn = keyboard_activate,
        },
        static extern fn keyboard_deactivate {
            sig = (instance: jlong),
            fn = keyboard_deactivate,
        },
        static extern fn keyboard_input {
            sig = (instance: jlong, scancode: jint, action: jint),
            fn = keyboard_input,
        },
        static extern fn keyboard_update {
            sig = (instance: jlong, scancode: jint, pressed: jboolean),
            fn = keyboard_update,
        },
        static extern fn output_size {
            sig = (instance: jlong) -> jint[],
            fn = output_size,
        },
        static extern fn output_bounds {
            sig = (instance: jlong) -> jint[],
            fn = output_bounds,
        },
        static extern fn output_resize {
            sig = (instance: jlong, width: jint, height: jint),
            fn = output_resize,
        },
        static extern fn output_set_bounds {
            sig = (instance: jlong, width: jint, height: jint),
            fn = output_set_bounds,
        },
        static extern fn free_surface {
            sig = (instance: jlong, surface_handle: jlong),
            fn = free_surface,
        },
        static extern fn free_toplevel {
            sig = (instance: jlong, toplevel_handle: jlong),
            fn = free_toplevel,
        },
        static extern fn free_popup {
            sig = (instance: jlong, popup_handle: jlong),
            fn = free_popup,
        },
        static extern fn load_desktop_entry {
            sig = (instance: jlong, path: JString) -> JRawDesktopEntry,
            fn = load_desktop_entry,
        },
        static extern fn load_desktop_entries {
            sig = (instance: jlong) -> JRawDesktopEntry[],
            fn = load_desktop_entries,
        },
        static extern fn render_svg {
            sig = (
                path: JString,
                width: jint,
                height: jint,
                buffer_ptr: jlong
            ) -> jboolean,
            name = "renderSVG",
            fn = render_svg,
        },
        static extern fn exec_app {
            sig = (instance: jlong, app_id: JString) -> jboolean,
            fn = exec_app,
        },
        static extern fn set_keymap_default {
            sig = (instance: jlong),
            fn = set_keymap_default,
        },
        static extern fn export_keymap {
            sig = (instance: jlong) -> JString,
            fn = export_keymap,
        },
        static extern fn set_keymap_from_str {
            sig = (instance: jlong, keymap: JString) -> jboolean,
            fn = set_keymap_from_str,
        },
        static extern fn check_dnd_request {
            sig = (instance: jlong) -> jint[],
            fn = check_dnd_request,
        },
        static extern fn check_dnd_active {
            sig = (instance: jlong) -> jboolean,
            fn = check_dnd_active,
        },
        static extern fn dnd_cancel {
            sig = (instance: jlong),
            fn = dnd_cancel,
        },
        static extern fn dnd_drop {
            sig = (instance: jlong),
            fn = dnd_drop,
        },
        static extern fn dnd_motion {
            sig = (
                instance: jlong,
                surface_handle: jlong,
                x: jdouble,
                y: jdouble
            ),
            fn = dnd_motion,
        },
        static extern fn dnd_icon {
            sig = (instance: jlong) -> jlong,
            fn = dnd_icon,
        },
    },
}

#[derive(Debug, Error)]
enum BridgeError {
    #[error(transparent)]
    JniError(#[from] jni::errors::Error),
    #[error(transparent)]
    Init(Box<dyn std::error::Error>),
    #[error("{0}")]
    Null(&'static str),
    #[error("Null WLC instance handle given. Function: {0}")]
    NullInstancePtr(&'static str),
    #[error("Null wayland surface handle given. Function: {0}")]
    NullSurfacePtr(&'static str),
    #[error("Null toplevel surface handle given. Function: {0}")]
    NullToplevelPtr(&'static str),
    #[error("Null popup surface handle given. Function: {0}")]
    NullPopupPtr(&'static str),
    #[error("Error converting OS string, was not UTF8")]
    OsStringToUtf8,
    #[error("Unknown pointer button {0} received")]
    UnknownPointerButton(jint),
    #[error("Unknown scroll direction {0} received")]
    UnknownScrollDirection(jint),
    #[error("Unknown keyboard state {0} received")]
    UnknownKeyboardState(jint),
    #[error("Width cannot be below 1")]
    NonPositiveWidth,
    #[error("Height cannot be below 1")]
    NonPositiveHeight,
}

macro_rules! jptr_to_instance {
    ($jptr:expr, $location:literal) => {
        match jptr_to_mut::<WaylandCraft>($jptr) {
            None => Err(BridgeError::NullInstancePtr($location)),
            Some(wlc) => Ok(wlc),
        }
    };
}

macro_rules! jptr_to_wl_surface {
    ($jptr:expr, $location:literal) => {
        match jptr_to_mut::<WlSurface>($jptr) {
            None => Err(BridgeError::NullSurfacePtr($location)),
            Some(wl_surface) => Ok(wl_surface),
        }
    };
}

macro_rules! jptr_to_toplevel {
    ($jptr:expr, $location:literal) => {
        match jptr_to_mut::<ToplevelSurface>($jptr) {
            None => Err(BridgeError::NullToplevelPtr($location)),
            Some(toplevel) => Ok(toplevel),
        }
    };
}

macro_rules! jptr_to_popup {
    ($jptr:expr, $location:literal) => {
        match jptr_to_mut::<PopupSurface>($jptr) {
            None => Err(BridgeError::NullPopupPtr($location)),
            Some(popup) => Ok(popup),
        }
    };
}

fn init<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    glfw_get_proc_address: jlong,
    egl_display: jlong,
) -> Result<jlong, BridgeError> {
    let dpy: EGLDisplay = (egl_display as usize) as EGLDisplay;
    let egl = EGLHelper::new(dpy, glfw_get_proc_address as usize);

    let instance = wlc_init(egl).map_err(BridgeError::Init)?;
    let instance_box = Box::new(instance);
    let ptr = Box::into_raw(instance_box);

    Ok(ptr.addr() as jlong)
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

fn update<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    jptr_to_instance!(instance, "update")?.update();

    Ok(())
}

fn socket<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JString<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance, "socket")?;
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
    let instance = jptr_to_instance!(instance, "x11Display")?;
    if let Some(ref s) = instance.state.satellite {
        Ok(JString::new(env, s.get_display())?)
    }
    else {
        Ok(JString::null())
    }
}

fn send_frame<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    surface_handle: jlong,
) -> Result<(), BridgeError> {
    let surface = jptr_to_wl_surface!(surface_handle, "sendFrame")?;

    with_surface_data(surface, |data| {
        let mut attr_guard = data.cached_state.get::<SurfaceAttributes>();
        let attr = attr_guard.deref_mut().current();
        for c in attr.frame_callbacks.drain(..) {
            c.done(get_time());
        }
    });

    Ok(())
}

// Get or insert an element and return its handle
fn insert_get_handle<T>(vec: &mut Vec<Box<T>>, elem: &T) -> jlong
where
    T: Clone + PartialEq,
{
    if !vec.iter().any(|b| **b == *elem) {
        vec.push(Box::new(elem.clone()));
    }

    let ptr: &mut T = vec.iter_mut().find(|r| ***r == *elem).unwrap();
    ((ptr as *mut T) as usize) as jlong
}

// Get an element and return its handle
// Element has to be in the list, otherwise this functions panics
fn get_handle<T>(vec: &[Box<T>], elem: &T) -> jlong
where
    T: Clone + PartialEq,
{
    let ptr: &T = vec.iter().find(|r| ***r == *elem).unwrap();
    ((ptr as *const T) as usize) as jlong
}

// Insert all elements that aren't in the list already
fn insert_all<T>(vec: &mut Vec<Box<T>>, elems: &[T])
where
    T: Clone + PartialEq,
{
    for elem in elems {
        if !vec.iter().any(|b| **b == *elem) {
            vec.push(Box::new(elem.clone()));
        }
    }
}

// Get handles of all elements in the list
fn get_all_handles<T>(vec: &mut [Box<T>]) -> Vec<jlong>
where
    T: Clone + PartialEq,
{
    vec.iter_mut()
        .map(|r| ((&mut **r) as *mut T) as usize as jlong)
        .collect()
}

// Remove element from list and free it
fn remove_element<T>(vec: &mut Vec<Box<T>>, handle: jlong)
where
    T: Clone + PartialEq,
{
    let ptr: *mut T = (handle as usize) as *mut T;
    let elem: &mut T = unsafe { &mut *ptr };
    vec.retain(|e| **e != *elem);
}

fn toplevels<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "toplevels")?;

    insert_all(
        &mut instance.bridge.toplevels,
        instance.state.xdg_state.toplevel_surfaces(),
    );

    instance.bridge.toplevels.retain(|t| t.alive());

    let toplevels = get_all_handles(&mut instance.bridge.toplevels);
    let array = JLongArray::new(env, toplevels.len())?;
    array.set_region(env, 0, &toplevels)?;
    Ok(array)
}

fn popups<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "popups")?;

    insert_all(
        &mut instance.bridge.popups,
        instance.state.xdg_state.popup_surfaces(),
    );

    instance.bridge.popups.retain(|t| t.alive());
    let popups = get_all_handles(&mut instance.bridge.popups);

    let array = JLongArray::new(env, popups.len())?;
    array.set_region(env, 0, &popups)?;
    Ok(array)
}

enum RequestsVec {
    Minimize,
    Maximize,
    Unmaximize,
    Fullscreen,
    Unfullscreen,
}

fn clear_requests<'local>(
    env: &mut Env<'local>,
    instance: &mut WaylandCraft,
    vec: RequestsVec,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let vec = match vec {
        RequestsVec::Minimize => &mut instance.state.requests.minimize,
        RequestsVec::Maximize => &mut instance.state.requests.maximize,
        RequestsVec::Unmaximize => &mut instance.state.requests.unmaximize,
        RequestsVec::Fullscreen => &mut instance.state.requests.fullscreen,
        RequestsVec::Unfullscreen => &mut instance.state.requests.unfullscreen,
    };

    let handles: Vec<jlong> = vec
        .iter()
        .filter(|t| t.alive())
        .map(|t| insert_get_handle(&mut instance.bridge.toplevels, t))
        .collect();

    vec.clear();

    let array = JLongArray::new(env, handles.len())?;
    array.set_region(env, 0, &handles)?;
    Ok(array)
}

fn minimize_req<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "minimizeReq")?;
    clear_requests(env, instance, RequestsVec::Minimize)
}

fn maximize_req<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "maximizeReq")?;
    clear_requests(env, instance, RequestsVec::Maximize)
}

fn unmaximize_req<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "unmaximizeReq")?;
    clear_requests(env, instance, RequestsVec::Unmaximize)
}

fn fullscreen_req<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "fullscreenReq")?;
    clear_requests(env, instance, RequestsVec::Fullscreen)
}

fn unfullscreen_req<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "unfullscreenReq")?;
    clear_requests(env, instance, RequestsVec::Unfullscreen)
}

fn move_request<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let instance = jptr_to_instance!(instance, "moveRequest")?;
    let serial = instance.state.requests.move_interactive.pop();

    let serial = match serial {
        Some(s) => s,
        None => return Ok(JPrimitiveArray::null()),
    };

    let serial = u32::from(serial) as jint;

    let array = JIntArray::new(env, 1)?;
    array.set_region(env, 0, &[serial])?;
    Ok(array)
}

fn resize_request<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let instance = jptr_to_instance!(instance, "resizeRequest")?;
    let req = instance.state.requests.resize_interactive.pop();

    let (serial, edges) = match req {
        Some(r) => r,
        None => return Ok(JPrimitiveArray::null()),
    };

    let serial = u32::from(serial) as jint;
    let edges = u32::from(edges) as jint;

    let array = JIntArray::new(env, 2)?;
    array.set_region(env, 0, &[serial, edges])?;
    Ok(array)
}

enum BufferAttachResult {
    Success,
    Error,
    NotManaged,
}

fn try_attach_shm(
    _instance: &mut WaylandCraft,
    env: &mut Env,
    jsurface: &WLCSurface,
    buf: &WlBuffer,
    surf_data: &SurfaceData,
) -> BufferAttachResult {
    let r = with_buffer_contents(buf, |ptr, _len, metadata| {
        let width = metadata.width as jint;
        let height = metadata.height as jint;
        let format = (metadata.format as u32) as jint;
        let stride = metadata.stride as jint;
        ensure_viewport_valid(surf_data, Size::new(width, height));

        let ptr =
            unsafe { ptr.offset(metadata.offset as isize) }.addr() as jlong;

        jsurface
            .attach_shm_buffer(env, ptr, width, height, format, stride)
            .unwrap();
    });

    match r {
        Ok(_) => BufferAttachResult::Success,
        Err(shm::BufferAccessError::NotManaged) => {
            BufferAttachResult::NotManaged
        }
        Err(_) => BufferAttachResult::Error,
    }
}

fn try_attach_single_pixel(
    _instance: &mut WaylandCraft,
    env: &mut Env,
    jsurface: &WLCSurface,
    buf: &WlBuffer,
    surf_data: &SurfaceData,
) -> BufferAttachResult {
    let pix = match get_single_pixel_buffer(buf) {
        Ok(p) => p,
        Err(_) => {
            return BufferAttachResult::NotManaged;
        }
    };

    ensure_viewport_valid(surf_data, Size::new(1, 1));

    let [r, g, b, a] = pix.rgba8888();
    jsurface
        .attach_single_pixel_buffer(
            env, r as jbyte, g as jbyte, b as jbyte, a as jbyte,
        )
        .unwrap();

    BufferAttachResult::Success
}

fn try_attach_dmabuf(
    instance: &mut WaylandCraft,
    env: &mut Env,
    jsurface: &WLCSurface,
    buf: &WlBuffer,
    surf_data: &SurfaceData,
) -> BufferAttachResult {
    let dmabuf = match get_dmabuf(buf) {
        Ok(d) => d,
        Err(_) => return BufferAttachResult::NotManaged,
    };

    let width = dmabuf.width() as jint;
    let height = dmabuf.height() as jint;
    ensure_viewport_valid(surf_data, Size::new(width, height));

    let weak = dmabuf.weak();
    let handle = insert_get_handle(&mut instance.bridge.dmabufs, &weak);

    let already_attached = jsurface.attach_dmabuf(env, handle).unwrap();

    if already_attached {
        return BufferAttachResult::Success;
    }

    let image = match instance.egl.dmabuf_to_image(dmabuf) {
        Ok(img) => img,
        Err(_) => return BufferAttachResult::Error,
    };

    jsurface
        .attach_new_dmabuf(env, handle, image.addr() as jlong, width, height)
        .unwrap();

    BufferAttachResult::Success
}

fn dmabufs<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "dmabufs")?;
    instance.bridge.dmabufs.retain(|d| !d.is_gone());

    let handles = get_all_handles(&mut instance.bridge.dmabufs);
    let array = JLongArray::new(env, handles.len())?;
    array.set_region(env, 0, &handles)?;
    Ok(array)
}

// Proxy to call the try_attach_* family of functions
fn try_attach_buffer(
    instance: &mut WaylandCraft,
    env: &mut Env,
    jsurface: &WLCSurface,
    buf: &WlBuffer,
    surf_data: &SurfaceData,
) -> Result<(), ()> {
    type TryAttachFn = fn(
        instance: &mut WaylandCraft,
        env: &mut Env,
        jsurface: &WLCSurface,
        buf: &WlBuffer,
        surf_data: &SurfaceData,
    ) -> BufferAttachResult;

    let funcs: [TryAttachFn; 3] =
        [try_attach_shm, try_attach_single_pixel, try_attach_dmabuf];
    for func in funcs {
        let result = func(instance, env, jsurface, buf, surf_data);
        match result {
            BufferAttachResult::NotManaged => continue,
            BufferAttachResult::Success => return Ok(()),
            BufferAttachResult::Error => return Err(()),
        }
    }

    unreachable!("Buffer did not match any attachment mechanism!")
}

fn update_surface_data<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    jsurface: WLCSurface<'local>,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "updateSurfaceData")?;

    let handle = jsurface.handle(env)?;
    let surface = jptr_to_ref::<WlSurface>(handle).ok_or_else(|| {
        BridgeError::Null("updateSufaceData: surfaceHandle is null")
    })?;

    with_states(surface, |data| {
        let mut attr_guard = data.cached_state.get::<SurfaceAttributes>();
        let attr = attr_guard.deref_mut().current();

        let (maybe_buf, mut remove_buf) = if let Some(assign) = &attr.buffer {
            match assign {
                BufferAssignment::NewBuffer(b) => (Some(b), false),
                BufferAssignment::Removed => (None, true),
            }
        } else {
            (None, false)
        };

        if let Some(buf) = maybe_buf {
            let r = try_attach_buffer(instance, env, &jsurface, buf, data);
            if r.is_err() {
                eprintln!("Buffer attach failed!");
                remove_buf = true;
            }

            // Done with buffer attachment
            // All buffers are immediately released because at this point they
            // are all already written to an independent OpenGL texture.
            // (including the dmabufs)
            buf.release();
            attr.buffer = None;
        }

        if remove_buf {
            jsurface.remove_buffer(env).unwrap();
        }

        let mut vp_data_guard = data.cached_state.get::<ViewportCachedState>();
        let vp_data = vp_data_guard.deref_mut().current();

        if let Some(src) = vp_data.src {
            jsurface
                .set_viewport_src(
                    env, src.loc.x, src.loc.y, src.size.w, src.size.h,
                )
                .unwrap();
        }

        if let Some(dst) = vp_data.dst {
            jsurface.set_viewport_dst(env, dst.w, dst.h).unwrap();
        }

        jsurface.clear_damage(env).unwrap();
        for damage in &attr.damage {
            match damage {
                Damage::Surface(d) => {
                    jsurface.add_surface_damage(
                        env,
                        d.loc.x,
                        d.loc.y,
                        d.size.w,
                        d.size.h
                    ).unwrap();
                },
                Damage::Buffer(d) => {
                    jsurface.add_buffer_damage(
                        env,
                        d.loc.x,
                        d.loc.y,
                        d.size.w,
                        d.size.h
                    ).unwrap();
                },
            }
        }
        attr.damage.clear();
    });

    Ok(())
}

fn toplevel_surface<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    toplevel_handle: jlong,
) -> Result<jlong, BridgeError> {
    let instance = jptr_to_instance!(instance, "toplevelSurface")?;
    let toplevel = jptr_to_toplevel!(toplevel_handle, "toplevelSurface")?;

    let surface = toplevel.wl_surface();

    Ok(insert_get_handle(&mut instance.bridge.surfaces, surface))
}

fn popup_surface<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    popup_handle: jlong,
) -> Result<jlong, BridgeError> {
    let instance = jptr_to_instance!(instance, "popupSurface")?;
    let popup = jptr_to_popup!(popup_handle, "popupSurface")?;

    let surface = popup.wl_surface();

    Ok(insert_get_handle(&mut instance.bridge.surfaces, surface))
}

fn popup_parent<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    popup_handle: jlong,
) -> Result<jlong, BridgeError> {
    let instance = jptr_to_instance!(instance, "popupParent")?;
    let popup = jptr_to_popup!(popup_handle, "popupParent")?;

    let parent_surface = match popup.get_parent_surface() {
        None => return Ok(0),
        Some(parent_surface) => parent_surface,
    };

    for toplevel in &instance.bridge.toplevels {
        if *toplevel.wl_surface() == parent_surface {
            return Ok(get_handle(&instance.bridge.toplevels, toplevel));
        }
    }

    for popup in &instance.bridge.popups {
        if *popup.wl_surface() == parent_surface {
            return Ok(get_handle(&instance.bridge.popups, popup));
        }
    }

    Ok(0)
}

fn popup_offset<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    popup_handle: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let popup = jptr_to_popup!(popup_handle, "popupOffset")?;

    let mut offset: [jint; 2] = [0, 0];

    popup.with_cached_state(|state| {
        let position = state.last_acked.map(|c| c.state.geometry.loc);

        if let Some(pos) = position {
            offset[0] = pos.x;
            offset[1] = pos.y;
        }
    });

    let array = JIntArray::new(env, 2)?;
    array.set_region(env, 0, &offset)?;
    Ok(array)
}

fn update_surface_tree<'local>(
    env: &mut Env<'local>,
    this: WaylandCraftBridge<'local>,
    instance: jlong,
    surface: WLCSurface<'local>,
) -> Result<WLCSurface<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance, "updateSurfaceTrees")?;

    let handle = surface.handle(env)?;
    let surface = jptr_to_ref::<WlSurface>(handle).ok_or_else(|| {
        BridgeError::Null("updateSufaceTree: surface is not alive")
    })?;

    let mut last_child = WLCSurface::null();

    with_surface_tree_upward(
        surface,
        None,
        |surface, _data, _parent| {
            TraversalAction::DoChildren(Some(surface.clone()))
        },
        |surface, data, parent| {
            let handle =
                insert_get_handle(&mut instance.bridge.surfaces, surface);
            let surface = this.get_or_create_surface(env, handle).unwrap();

            // Set the WLCSurface parentHandle
            let parent_handle = if let Some(p) = parent {
                insert_get_handle(&mut instance.bridge.surfaces, p)
            } else {
                0
            };

            surface.set_parent_handle(env, parent_handle).unwrap();

            // Set last child to point to this current surface
            if !last_child.is_null() {
                last_child.set_next_child(env, &surface).unwrap();
            }

            // Set this surfaces nextChild to null
            surface.set_next_child(env, WLCSurface::null()).unwrap();

            // Set this surfaces prevChild to the last child
            surface.set_prev_child(env, &last_child).unwrap();

            // Mark this surface as visited
            surface.set_visited(env, true).unwrap();

            // Set subsurface location
            let (sx, sy) = if data.cached_state.has::<SubsurfaceCachedState>() {
                let mut subattr_guard =
                    data.cached_state.get::<SubsurfaceCachedState>();
                let subattr = subattr_guard.deref_mut().current();
                (subattr.location.x, subattr.location.y)
            } else {
                (0, 0)
            };

            surface.set_xoff(env, sx).unwrap();
            surface.set_yoff(env, sy).unwrap();

            last_child = surface;
        },
        |_surface, _data, _parent| true,
    );

    Ok(last_child)
}

fn pointer_motion<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    x: jdouble,
    y: jdouble,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "pointerMotion")?;
    instance.state.seat.pointer_motion(x, y);

    Ok(())
}

fn pointer_motion_focus<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    surface_handle: jlong,
    x: jdouble,
    y: jdouble,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "pointerMotionFocus")?;
    let surface = jptr_to_ref(surface_handle);
    instance.state.seat.pointer_motion_focus(surface, x, y);

    Ok(())
}

fn pointer_rel_motion<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    dx: jdouble,
    dy: jdouble,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "pointerRelMotion")?;
    instance.state.seat.pointer_relative_motion(dx, dy);

    Ok(())
}

fn maybe_pointer_lock<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    surface_handle: jlong,
) -> Result<jboolean, BridgeError> {
    let instance = jptr_to_instance!(instance, "maybePointerLock")?;
    let Some(surface) = jptr_to_ref(surface_handle) else {
        return Ok(false);
    };

    Ok(instance.state.seat.pointer_lock(surface))
}

fn pointer_unlock<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "pointerUnlock")?;
    instance.state.seat.pointer_unlock();

    Ok(())
}

fn pointer_leave<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "pointerLeave")?;
    instance.state.seat.pointer_motion_focus(None, 0.0, 0.0);

    Ok(())
}

fn pointer_button<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    button: jint,
    state: jint,
) -> Result<jint, BridgeError> {
    let instance = jptr_to_instance!(instance, "pointerButton")?;

    let state = match state {
        0 => ButtonState::Released,
        1 => ButtonState::Pressed,
        _ => return Err(BridgeError::UnknownPointerButton(state)),
    };

    Ok(instance.state.seat.pointer_button(button as u32, state) as jint)
}

fn pointer_axis<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    axis: jint,
    value: jdouble,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "pointerAxis")?;

    let axis = match axis {
        0 => Axis::VerticalScroll,
        1 => Axis::HorizontalScroll,
        _ => {
            return Err(BridgeError::UnknownScrollDirection(axis));
        }
    };

    instance.state.seat.pointer_axis(axis, value);

    Ok(())
}

fn cursor_shape<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<jint, BridgeError> {
    let instance = jptr_to_instance!(instance, "cursorShape")?;

    let shape = match instance.state.seat.cursor_shape {
        Some(shape) => shape as jint,
        None => -1,
    };

    Ok(shape)
}

fn keyboard_focus<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    surface_handle: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "keyboardFocus")?;
    let toplevel: Option<&ToplevelSurface> = jptr_to_ref(surface_handle);

    let surface = toplevel.map(|t| t.wl_surface().clone());

    // Update the client gaining keyboard focus with the clipboard contents
    let client = surface.as_ref().and_then(|s| s.client());
    instance.state.data.update_clipboard_client(client);

    match surface {
        Some(s) => instance.state.seat.keyboard_focus(s),
        None => instance.state.seat.keyboard_unfocus(),
    };

    instance
        .state
        .xdg_state
        .toplevel_surfaces()
        .iter()
        .for_each(|t| {
            t.with_pending_state(|state| {
                state.states.unset(xdg_toplevel::State::Activated);
            });
        });

    if let Some(t) = toplevel {
        t.with_pending_state(|state| {
            state.states.set(xdg_toplevel::State::Activated);
        })
    }

    instance
        .state
        .xdg_state
        .toplevel_surfaces()
        .iter()
        .for_each(|t| {
            t.send_pending_configure();
        });

    Ok(())
}

fn keyboard_activate<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "keyboardActivate")?;
    instance.state.seat.activate_keyboard();

    Ok(())
}

fn keyboard_deactivate<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "keyboardDeactivate")?;
    instance.state.seat.deactivate_keyboard();

    Ok(())
}

fn keyboard_input<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    scancode: jint,
    action: jint,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "keyboardInput")?;

    let scancode = scancode as u32;
    let action = match action {
        0 => KeyState::Released,
        1 => KeyState::Pressed,
        _ => {
            return Err(BridgeError::UnknownKeyboardState(action));
        }
    };

    instance.state.seat.keyboard_key(scancode, action);

    Ok(())
}

fn keyboard_update<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    scancode: jint,
    pressed: jboolean,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "keyboardUpdate")?;
    instance
        .state
        .seat
        .keyboard_update_xkb(scancode as u32, pressed);

    Ok(())
}

fn fullscreened<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jlong>, BridgeError> {
    let instance = jptr_to_instance!(instance, "fullscreened")?;

    let mut handles: Vec<jlong> = vec![];
    for toplevel in instance.state.xdg_state.toplevel_surfaces() {
        let fullscreen = toplevel.with_committed_state(|state| {
            state
                .map(|s| s.states.contains(xdg_toplevel::State::Fullscreen))
                .unwrap_or(false)
        });

        if !fullscreen {
            continue;
        }

        handles
            .push(insert_get_handle(&mut instance.bridge.toplevels, toplevel));
    }

    let array = JLongArray::new(env, handles.len())?;
    array.set_region(env, 0, &handles)?;
    Ok(array)
}

fn output_size<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let instance = jptr_to_instance!(instance, "outputSize")?;

    let size = instance.state.output.size();
    let size: [jint; 2] = [size.w, size.h];

    let array = JIntArray::new(env, 2)?;
    array.set_region(env, 0, &size)?;
    Ok(array)
}

fn output_bounds<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let instance = jptr_to_instance!(instance, "outputBounds")?;

    let bounds = instance.state.output.bounds();
    let bounds: [jint; 2] = [bounds.w, bounds.h];

    let array = JIntArray::new(env, 2)?;
    array.set_region(env, 0, &bounds)?;
    Ok(array)
}

fn output_resize<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    width: jint,
    height: jint,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "outputResize")?;
    let size = instance.state.output.size();
    let width_changed = size.w != width;
    let height_changed = size.h != height;

    if width < 1 {
        return Err(BridgeError::NonPositiveWidth);
    } else if height < 1 {
        return Err(BridgeError::NonPositiveHeight);
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

fn output_set_bounds<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    width: jint,
    height: jint,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "outputSetBounds")?;
    let bounds = instance.state.output.bounds();
    let width_changed = bounds.w != width;
    let height_changed = bounds.h != height;

    if width < 1 {
        return Err(BridgeError::NonPositiveWidth);
    } else if height < 1 {
        return Err(BridgeError::NonPositiveHeight);
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

fn check_input_region<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    surface_handle: jlong,
    x: jdouble,
    y: jdouble,
) -> Result<jboolean, BridgeError> {
    let Some(surface) = jptr_to_ref(surface_handle) else {
        return Ok(false);
    };

    let point: Point<f64, Logical> = Point::new(x, y);

    Ok(with_states(surface, |data| {
        let mut attr_guard = data.cached_state.get::<SurfaceAttributes>();
        let attr = attr_guard.deref_mut().current();
        if let Some(r) = &attr.input_region {
            r.contains(point.to_i32_floor())
        } else {
            true
        }
    }))
}

fn surface_xdg_geometry<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    surface_handle: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let surface = match jptr_to_ref(surface_handle) {
        Some(s) => s,
        None => return Ok(JIntArray::null()),
    };

    let geometry: Option<[jint; 4]> = with_states(surface, |states| {
        let mut guard = states.cached_state.get::<SurfaceCachedState>();
        guard
            .current()
            .geometry
            .map(|r| [r.loc.x, r.loc.y, r.size.w, r.size.h])
    });

    if let Some(geometry) = geometry {
        let array = JIntArray::new(env, 4)?;
        array.set_region(env, 0, &geometry)?;
        Ok(array)
    } else {
        Ok(JIntArray::null())
    }
}

fn toplevel_title<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    toplevel_handle: jlong,
) -> Result<JString<'local>, BridgeError> {
    let toplevel = jptr_to_toplevel!(toplevel_handle, "toplevelTitle")?;

    let surface = toplevel.wl_surface();

    let title = with_states(surface, |states| {
        let attr_guard = states
            .data_map
            .get::<XdgToplevelSurfaceData>()
            .unwrap()
            .lock()
            .unwrap();

        attr_guard.title.clone()
    });

    if let Some(title) = title {
        Ok(env.new_string(title)?)
    } else {
        Ok(JString::null())
    }
}

fn toplevel_app_id<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    toplevel_handle: jlong,
) -> Result<JString<'local>, BridgeError> {
    let toplevel = jptr_to_toplevel!(toplevel_handle, "toplevelAppId")?;

    let surface = toplevel.wl_surface();

    let app_id = with_states(surface, |states| {
        let attr_guard = states
            .data_map
            .get::<XdgToplevelSurfaceData>()
            .unwrap()
            .lock()
            .unwrap();

        attr_guard.app_id.clone()
    });

    if let Some(app_id) = app_id {
        Ok(env.new_string(app_id)?)
    } else {
        Ok(JString::null())
    }
}

fn toplevel_resize<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    toplevel_handle: jlong,
    width: jint,
    height: jint,
    interactive: jboolean,
) -> Result<(), BridgeError> {
    let toplevel = jptr_to_toplevel!(toplevel_handle, "toplevelResize")?;

    toplevel.with_pending_state(|state| {
        state.size = Some(Size::new(width, height));
        state.states.unset(xdg_toplevel::State::Maximized);
        state.states.unset(xdg_toplevel::State::Fullscreen);
        if interactive {
            state.states.set(xdg_toplevel::State::Resizing);
        } else {
            state.states.unset(xdg_toplevel::State::Resizing);
        }
    });

    toplevel.send_pending_configure();

    Ok(())
}

fn toplevel_resize_ovr<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    toplevel_handle: jlong,
    width: jint,
    height: jint,
) -> Result<(), BridgeError> {
    let toplevel = jptr_to_toplevel!(toplevel_handle, "toplevelResizeOvr")?;

    toplevel.with_pending_state(|state| {
        state.size = Some(Size::new(width, height));
        state.states.unset(xdg_toplevel::State::Resizing);
    });

    toplevel.send_pending_configure();

    Ok(())
}

fn toplevel_maximize<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    toplevel_handle: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "toplevelMaximize")?;
    let toplevel = jptr_to_toplevel!(toplevel_handle, "toplevelMaximize")?;

    toplevel.with_pending_state(|state| {
        if state.states.contains(xdg_toplevel::State::Fullscreen) {
            return;
        }
        let output = &instance.state.output;
        state.size = Some(output.bounds());
        state.states.set(xdg_toplevel::State::Maximized);
    });

    toplevel.send_configure();
    Ok(())
}

fn toplevel_fullscreen<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    toplevel_handle: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "toplevelFullscreen")?;
    let toplevel = jptr_to_toplevel!(toplevel_handle, "toplevelFullscreen")?;

    toplevel.with_pending_state(|state| {
        let output = &instance.state.output;
        state.size = Some(output.size());
        state.states.set(xdg_toplevel::State::Fullscreen);
    });

    toplevel.send_configure();
    Ok(())
}

fn free_surface<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    surface_handle: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "freeSurface")?;
    remove_element(&mut instance.bridge.surfaces, surface_handle);

    Ok(())
}

fn free_toplevel<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    toplevel_handle: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "freeToplevel")?;
    remove_element(&mut instance.bridge.toplevels, toplevel_handle);

    Ok(())
}

fn free_popup<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    popup_handle: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "freePopup")?;
    remove_element(&mut instance.bridge.popups, popup_handle);

    Ok(())
}

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

fn load_desktop_entry<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    path: JString<'local>,
) -> Result<JRawDesktopEntry<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance, "loadDesktopEntry")?;
    let path: PathBuf = path.try_to_string(env)?.into();
    let entry = match instance.xdg.load_entry(path) {
        Some(e) => e,
        None => return Ok(JRawDesktopEntry::null()),
    };

    raw_desktop_entry_to_java(env, &entry)
}

fn load_desktop_entries<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JObjectArray<'local, JRawDesktopEntry<'local>>, BridgeError> {
    let instance = jptr_to_instance!(instance, "loadDesktopEntries")?;
    let entries = instance.xdg.get_raw_entries();
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

fn render_svg<'local>(
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

fn exec_app<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    app_id: JString<'local>,
) -> Result<jboolean, BridgeError> {
    let instance = jptr_to_instance!(instance, "execApp")?;
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

    Ok(instance.xdg.exec_app(app_id, env_vars))
}

fn set_keymap_default<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "setKeymapDefault")?;
    instance.state.seat.change_keymap_to_default();

    Ok(())
}

fn export_keymap<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JString<'local>, BridgeError> {
    let instance = jptr_to_instance!(instance, "exportKeymap")?;
    let keymap_str = instance.state.seat.export_keymap();
    Ok(JString::new(env, keymap_str)?)
}

fn set_keymap_from_str<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    keymap: JString<'local>,
) -> Result<jboolean, BridgeError> {
    let instance = jptr_to_instance!(instance, "setKeymapFromStr")?;
    let keymap_str = keymap.try_to_string(env)?;
    Ok(instance.state.seat.change_keymap_from_str(keymap_str))
}

fn check_dnd_request<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<JPrimitiveArray<'local, jint>, BridgeError> {
    let instance = jptr_to_instance!(instance, "checkDndRequest")?;
    let serial = match instance.state.data.check_dnd_request() {
        Some(r) => r as jint,
        None => return Ok(JIntArray::null()),
    };

    let array = JIntArray::new(env, 1)?;
    array.set_region(env, 0, &[serial])?;
    Ok(array)
}

fn check_dnd_active<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<jboolean, BridgeError> {
    let instance = jptr_to_instance!(instance, "checkDndActive")?;
    Ok(instance.state.data.dnd.is_some())
}

fn dnd_cancel<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "dndCancel")?;
    instance.state.data.dnd_cancel();

    Ok(())
}

fn dnd_drop<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "dndDrop")?;
    instance.state.data.dnd_drop();

    Ok(())
}

fn dnd_motion<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    surface_handle: jlong,
    x: jdouble,
    y: jdouble,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance, "dndMotion")?;
    let surface = jptr_to_ref(surface_handle);
    instance.state.data.dnd_motion(surface, x, y);

    Ok(())
}

fn dnd_icon<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
) -> Result<jlong, BridgeError> {
    let instance = jptr_to_instance!(instance, "dndIcon")?;
    let Some(dnd) = &instance.state.data.dnd else {
        return Ok(0);
    };

    match dnd.icon.as_ref() {
        Some(icon) => {
            Ok(insert_get_handle(&mut instance.bridge.surfaces, icon))
        }
        None => Ok(0),
    }
}
