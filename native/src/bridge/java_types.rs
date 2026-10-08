#![allow(clippy::too_many_arguments)]

use crate::bridge;
use jni::{bind_java_type, sys::jint};
use smithay::backend::drm::CreateDrmNodeError;
use thiserror::Error;

bind_java_type! {
    rust_type = pub WLCToplevel,
    java_type = dev.evvie.waylandcraft.bridge.WLCToplevel,

    type_map {
        WLCSurface => dev.evvie.waylandcraft.bridge.WLCSurface,
    },

    constructors {
        fn new(handle: jlong, surface: WLCSurface),
    },

    fields {
        handle: jlong,
        surface: WLCSurface,
        title: JString,
        app_id {
            sig = JString,
            name = "appID",
        },
        fullscreen: jboolean,
    },

    methods {
        fn default_geometry(),
        fn update_geometry(x: jint, y: jint, width: jint, height: jint),
    },

}

bind_java_type! {
    rust_type = pub WLCPopup,
    java_type = dev.evvie.waylandcraft.bridge.WLCPopup,

    type_map {
        WLCAbstractWindow => dev.evvie.waylandcraft.bridge.WLCAbstractWindow,
        WLCSurface => dev.evvie.waylandcraft.bridge.WLCSurface,
    },

    constructors {
        fn new(handle: jlong, surface: WLCSurface),
    },

    fields {
        handle: jlong,
        surface: WLCSurface,
        parent: WLCAbstractWindow,
        offset_x: jint,
        offset_y: jint,
    },
}

bind_java_type! {
    rust_type = pub WLCAbstractWindow,
    java_type = dev.evvie.waylandcraft.bridge.WLCAbstractWindow,
}

bind_java_type! {
    rust_type = pub WLCSurface,
    java_type = dev.evvie.waylandcraft.bridge.WLCSurface,

    type_map {
        JDmabufTexture =>
            "dev.evvie.waylandcraft.render.BufferTexture$DmabufTexture",
    },

    constructors {
        fn new(handle: jlong),
    },

    fields {
        handle: jlong,
        parent: WLCSurface,
        children: WLCSurface[],
        surface_draw_tree: WLCSurface[],
        surface_input_tree: WLCSurface[],
        xoff: jint,
        yoff: jint,
        x_subpos: jint,
        y_subpos: jint,
    },

    methods {
        pub fn remove_buffer(),
        pub fn set_viewport_src(
            x: jdouble,
            y: jdouble,
            width: jdouble,
            height: jdouble
        ),
        pub fn unset_viewport_src(),
        pub fn set_viewport_dst(width: jint, height: jint),
        pub fn attach_shm_buffer(
            ptr: jlong,
            width: jint,
            height: jint,
            format: jint,
            stride: jint
        ),
        pub fn attach_single_pixel_buffer(
            red: jbyte,
            green: jbyte,
            blue: jbyte,
            alpha: jbyte
        ),
        pub fn attach_dmabuf(
            buf: JDmabufTexture,
        ),
        pub fn clear_damage(),
        pub fn add_buffer_damage(x: jint, y: jint, width: jint, height: jint),
        pub fn add_surface_damage(x: jint, y: jint, width: jint, height: jint),
        pub fn calculate_subpos(),
        pub fn commit(),
    },

    native_methods {
        extern fn send_frame {
            sig = (),
            fn = bridge::compositor::send_frame,
        },
        extern fn input_region_contains {
            sig = (x: jdouble, y: jdouble) -> jboolean,
            fn = bridge::compositor::input_region_contains,
        },
    },
}

bind_java_type! {
    rust_type = pub JDmabufTexture,
    java_type = "dev.evvie.waylandcraft.render.BufferTexture$DmabufTexture",

    methods = {
        pub fn free_internal(),
    },
}

bind_java_type! {
    rust_type = pub JBufferTexture,
    java_type = dev.evvie.waylandcraft.render.BufferTexture,

    type_map {
        JDmabufTexture =>
            "dev.evvie.waylandcraft.render.BufferTexture$DmabufTexture",
        JDmabuf => dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf,
    },

    methods = {
        pub static fn create_dmabuf_texture(buf: JDmabuf) -> JDmabufTexture,
    },
}

bind_java_type! {
    rust_type = pub JDmabufFormat,
    java_type = dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat,

    constructors {
        fn new(
            code: jint,
            modifier: jlong
        )
    },

    fields {
        code: jint,
        modifier: jlong
    },
}

bind_java_type! {
    rust_type = pub JDmabufPlane,
    java_type = dev.evvie.waylandcraft.bridge.dmabuf.DmabufPlane,

    constructors {
        fn new(
            fd: jint,
            offset: jint,
            stride: jint
        )
    },
}

bind_java_type! {
    rust_type = pub JDmabuf,
    java_type = dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf,

    constructors {
        fn new(
            width: jint,
            height: jint,
            format: jint,
            modifier: jlong,
            planes: dev.evvie.waylandcraft.bridge.dmabuf.DmabufPlane[]
        )
    },
}

bind_java_type! {
    rust_type = pub JDmabufFeedbackData,
    java_type = dev.evvie.waylandcraft.bridge.dmabuf.DmabufFeedbackData,

    constructors {
        fn new(
            drm_device: jlong,
            formats: dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat[],
        )
    },

    fields {
        drm_device: jlong,
        formats: dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat[],
    },
}

bind_java_type! {
    rust_type = pub JRawDesktopEntry,
    java_type = dev.evvie.waylandcraft.desktop.RawDesktopEntry,

    constructors {
        fn new(
            app_id: JString,
            name: JString,
            generic_name: JString,
            exec: JString,
            exec_terminal: jboolean,
            comment: JString,
            keywords: JString[],
            categories: JString[],
            visible: jboolean,
            icon_path: JString
        )
    },
}

bind_java_type! {
    rust_type = pub WaylandCraftBridge,
    java_type = dev.evvie.waylandcraft.bridge.WaylandCraftBridge,

    type_map {
        WLCSurface => dev.evvie.waylandcraft.bridge.WLCSurface,
        WLCToplevel => dev.evvie.waylandcraft.bridge.WLCToplevel,
        WLCPopup => dev.evvie.waylandcraft.bridge.WLCPopup,
        JRawDesktopEntry => dev.evvie.waylandcraft.desktop.RawDesktopEntry,
        JDmabufFormat => dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat,
        JDmabufPlane => dev.evvie.waylandcraft.bridge.dmabuf.DmabufPlane,
        JDmabuf => dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf,
        JDmabufFeedbackData =>
            dev.evvie.waylandcraft.bridge.dmabuf.DmabufFeedbackData,
    },

    methods {
        fn add_surface(WLCSurface),
        fn delete_surface(WLCSurface),
        fn add_toplevel(WLCToplevel),
        fn delete_toplevel(WLCToplevel),
        fn call_on_maximize(WLCToplevel),
        fn call_on_unmaximize(WLCToplevel),
        fn call_on_fullscreen(WLCToplevel),
        fn call_on_unfullscreen(WLCToplevel),
        fn call_on_minimize(WLCToplevel),
        fn call_on_move(WLCToplevel, jint),
        fn call_on_resize(WLCToplevel, jint, jint),
        fn call_on_dnd {
            sig = (WLCToplevel, jint),
            name = "callOnDND",
        },
    },

    constructors {
        fn new(jlong),
    },

    native_methods {
        /* General */
        static extern fn init {
            sig = (
                dmabuf_feedback: JDmabufFeedbackData,
            ) -> WaylandCraftBridge,
            fn = bridge::init,
        },
        static extern fn shutdown {
            sig = (instance: jlong),
            fn = bridge::shutdown,
        },
        static extern fn dispatch_clients {
            sig = (instance: jlong),
            fn = bridge::dispatch_clients,
        },
        static extern fn flush_display {
            sig = (instance: jlong),
            fn = bridge::flush_display
        },
        static extern fn socket {
            sig = (instance: jlong) -> JString,
            fn = bridge::socket,
        },
        static extern fn x11_display {
            sig = (instance: jlong) -> JString,
            fn = bridge::x11_display,
        },

        /* DRM */
        static extern fn drm_device_by_path {
            sig = (path: JString) -> jlong,
            fn = bridge::drm::drm_device_by_path,
        },
        static extern fn drm_device_by_major_minor {
            sig = (major: jint, minor: jint) -> jlong,
            fn = bridge::drm::drm_device_by_major_minor,
        },

        /* Seat */
        static extern fn pointer_motion {
            sig = (instance: jlong, x: jdouble, y: jdouble),
            fn = bridge::seat::pointer_motion,
        },
        static extern fn pointer_motion_focus {
            sig = (
                instance: jlong,
                surface: WLCSurface,
                x: jdouble,
                y: jdouble
            ),
            fn = bridge::seat::pointer_motion_focus,
        },
        static extern fn pointer_rel_motion {
            sig = (instance: jlong, dx: jdouble, dy: jdouble),
            fn = bridge::seat::pointer_rel_motion,
        },
        static extern fn maybe_pointer_lock {
            sig = (instance: jlong, surface: WLCSurface) -> jboolean,
            fn = bridge::seat::maybe_pointer_lock,
        },
        static extern fn pointer_unlock {
            sig = (instance: jlong),
            fn = bridge::seat::pointer_unlock,
        },
        static extern fn pointer_leave {
            sig = (instance: jlong),
            fn = bridge::seat::pointer_leave,
        },
        static extern fn pointer_button {
            sig = (instance: jlong, button: jint, state: jint) -> jint,
            fn = bridge::seat::pointer_button,
        },
        static extern fn pointer_axis {
            sig = (instance: jlong, axis: jint, value: jdouble),
            fn = bridge::seat::pointer_axis,
        },
        static extern fn cursor_shape {
            sig = (instance: jlong) -> jint,
            fn = bridge::seat::cursor_shape,
        },
        static extern fn keyboard_focus {
            sig = (instance: jlong, toplevel: WLCToplevel),
            fn = bridge::seat::keyboard_focus,
        },
        static extern fn keyboard_activate {
            sig = (instance: jlong),
            fn = bridge::seat::keyboard_activate,
        },
        static extern fn keyboard_deactivate {
            sig = (instance: jlong),
            fn = bridge::seat::keyboard_deactivate,
        },
        static extern fn keyboard_input {
            sig = (instance: jlong, scancode: jint, action: jint),
            fn = bridge::seat::keyboard_input,
        },
        static extern fn keyboard_update {
            sig = (instance: jlong, scancode: jint, pressed: jboolean),
            fn = bridge::seat::keyboard_update,
        },
        static extern fn set_keymap_from_str {
            sig = (instance: jlong, keymap: JString) -> jboolean,
            fn = bridge::seat::set_keymap_from_str,
        },

        /* Shell */
        static extern fn toplevel_resize {
            sig = (
                instance: jlong,
                toplevel: WLCToplevel,
                width: jint,
                height: jint,
                interactive: jboolean,
            ),
            fn = bridge::shell::toplevel_resize,
        },
        static extern fn toplevel_resize_ovr {
            sig = (
                instance: jlong,
                toplevel: WLCToplevel,
                width: jint,
                height: jint,
            ),
            fn = bridge::shell::toplevel_resize_ovr,
        },
        static extern fn toplevel_maximize {
            sig = (instance: jlong, toplevel: WLCToplevel),
            fn = bridge::shell::toplevel_maximize,
        },
        static extern fn toplevel_fullscreen {
            sig = (instance: jlong, toplevel: WLCToplevel),
            fn = bridge::shell::toplevel_fullscreen,
        },

        /* Output */
        static extern fn output_size {
            sig = (instance: jlong) -> jint[],
            fn = bridge::output::output_size,
        },
        static extern fn output_bounds {
            sig = (instance: jlong) -> jint[],
            fn = bridge::output::output_bounds,
        },
        static extern fn output_resize {
            sig = (instance: jlong, width: jint, height: jint),
            fn = bridge::output::output_resize,
        },
        static extern fn output_set_bounds {
            sig = (instance: jlong, width: jint, height: jint),
            fn = bridge::output::output_set_bounds,
        },
    },
}

#[derive(Debug, Error)]
pub enum BridgeError {
    #[error(transparent)]
    JniError(#[from] jni::errors::Error),
    #[error(transparent)]
    Init(Box<dyn std::error::Error>),
    #[error(transparent)]
    DrmNodeError(#[from] CreateDrmNodeError),
    #[error("Received null instance handle")]
    NullInstancePtr,
    #[error("Error converting OS string to UTF-8")]
    OsStringToUtf8,
    #[error("Surface is already gone")]
    SurfaceGone,
    #[error("Surface is null")]
    SurfaceNull,
    #[error("Toplevel is already gone")]
    ToplevelGone,
    #[error("Toplevel is null")]
    ToplevelNull,
    #[error("Unknown pointer button {0} received")]
    UnknownPointerButton(jint),
    #[error("Unknown scroll direction {0} received")]
    UnknownScrollDirection(jint),
    #[error("Unknown keyboard state {0} received")]
    UnknownKeyboardState(jint),
    #[error("Invalid output size")]
    InvalidOutputSize,
}
