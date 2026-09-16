#![allow(clippy::too_many_arguments)]

use jni::bind_java_type;

bind_java_type! {
    rust_type = WLCSurface,
    rust_type_vis = pub,
    java_type = dev.evvie.waylandcraft.bridge.WLCSurface,

    fields {
        handle: jlong,
        visited: jboolean,
        next_child: WLCSurface,
        prev_child: WLCSurface,
        parent_handle: jlong,
        xoff: jint,
        yoff: jint,
    },

    methods {
        pub fn remove_buffer(),
        pub fn set_viewport_src(
            x: jdouble,
            y: jdouble,
            width: jdouble,
            height: jdouble
        ),
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
        pub fn attach_dmabuf(handle: jlong) -> jboolean,
        pub fn clear_damage(),
        pub fn add_buffer_damage(x: jint, y: jint, width: jint, height: jint),
        pub fn add_surface_damage(x: jint, y: jint, width: jint, height: jint),
    },
}

bind_java_type! {
    rust_type = JDmabufFormat,
    rust_type_vis = pub,
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
    rust_type = JDmabufPlane,
    rust_type_vis = pub,
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
    rust_type = JDmabuf,
    rust_type_vis = pub,
    java_type = dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf,

    constructors {
        fn new(
            handle: jlong,
            width: jint,
            height: jint,
            format: jint,
            modifier: jlong,
            planes: dev.evvie.waylandcraft.bridge.dmabuf.DmabufPlane[]
        )
    },
}

bind_java_type! {
    rust_type = JDmabufFeedbackData,
    rust_type_vis = pub,
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
    rust_type = JRawDesktopEntry,
    rust_type_vis = pub,
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
