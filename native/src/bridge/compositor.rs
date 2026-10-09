use crate::{
    WLCState,
    bridge::{
        self, BridgeError,
        java_types::*,
        shell::{
            popup_commit, popup_for_surface, toplevel_commit,
            toplevel_for_surface,
        },
        utils::with_env,
    },
    utils::get_time,
};
use jni::{
    Env,
    objects::{Global, JObjectArray},
    sys::{jbyte, jdouble, jint, jlong},
};
use smithay::{
    backend::allocator::Buffer,
    reexports::wayland_server::{
        Resource, Weak,
        protocol::wl_buffer::WlBuffer,
        protocol::wl_surface::WlSurface,
    },
    utils::{Logical, Point, Size},
    wayland::{
        compositor::{
            get_children, get_parent, is_sync_subsurface, with_states,
            with_surface_tree_upward, with_surface_tree_downward,
            BufferAssignment, SubsurfaceCachedState, SurfaceAttributes,
            SurfaceData, TraversalAction,
        },
        single_pixel_buffer::get_single_pixel_buffer,
        shm::{self, with_buffer_contents},
        viewporter::{ensure_viewport_valid, ViewportCachedState},
        dmabuf::get_dmabuf,
    },
};
use std::sync::Arc;
use std::ops::DerefMut;

pub struct MySurfaceInner(pub Global<WLCSurface<'static>>);
type MySurface = Arc<MySurfaceInner>;

pub fn surface_user_data(
    surface: &WlSurface
) -> MySurface {
    with_states(surface, |data| {
        data
            .data_map
            .get::<MySurface>()
            .unwrap()
            .clone()
    })
}

// Turns &WlSurface into &WLCSurface
#[macro_export]
macro_rules! get_java_surface {
    ($surface:expr) => {
        (&$crate::bridge::compositor::surface_user_data($surface).0)
    };
}
pub use get_java_surface;

// Turns Option<WlSurface> into (non-)null &WLCSurface
#[macro_export]
macro_rules! get_java_surface_opt {
    ($surface:expr) => {
        match $surface {
            Some(s) => $crate::get_java_surface!(&s),
            None => &$crate::bridge::java_types::WLCSurface::null(),
        }
    };
}
pub use get_java_surface_opt;

pub fn get_java_surfaces<'local>(
    env: &mut Env<'local>,
    surfaces: &[WlSurface]
) -> Result<JObjectArray<'local, WLCSurface<'local>>, BridgeError> {
    let array = JObjectArray::<WLCSurface>::new(
        env,
        surfaces.len(),
        WLCSurface::null(),
    )?;

    for (idx, surface) in surfaces.iter().enumerate() {
        let jsurface = get_java_surface!(surface);
        array.set_element(env, idx, jsurface)?;
    }

    Ok(array)
}

pub fn new_surface(state: &mut WLCState, surface: &WlSurface) {
    with_env(|env| _new_surface(env, state, surface));
}

fn _new_surface<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    surface: &WlSurface
) -> Result<(), BridgeError> {
    // Create handle from boxed WlSurface weak reference
    let weak: Weak<WlSurface> = surface.downgrade();
    let weak = Box::new(weak);
    let ptr = (Box::into_raw(weak) as usize) as jlong;

    // Create java surface and insert global reference into wl_surface data
    let jsurface = WLCSurface::new(env, ptr)?;
    let jsurface_ref = env.new_global_ref(&jsurface)?;
    let my_surface = Arc::new(MySurfaceInner(jsurface_ref));

    with_states(surface, |data| {
        data.data_map.insert_if_missing(|| my_surface);
    });

    // Tell the java code about the new surface
    state.bridge.java.add_surface(env, jsurface)?;

    Ok(())
}

pub fn surface_destroyed(state: &mut WLCState, surface: &WlSurface) {
    with_env(|env| _surface_destroyed(env, state, surface));
}

fn _surface_destroyed<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    surface: &WlSurface
) -> Result<(), BridgeError> {
    // Remove surface in java bridge code
    let jsurface = get_java_surface!(surface);
    state.bridge.java.delete_surface(env, jsurface)?;

    // Remove and destroy its handle
    let ptr = (jsurface.handle(env)? as usize) as *mut Weak<WlSurface>;
    let ptr = unsafe { Box::from_raw(ptr) };
    drop(ptr);
    jsurface.set_handle(env, 0)?;

    // The java global reference will get dropped with the surface

    Ok(())
}

pub fn subsurface_created(
    state: &mut WLCState,
    surface: &WlSurface,
    parent: &WlSurface
) {
    with_env(|env| _subsurface_created(env, state, surface, parent));
}

fn _subsurface_created<'local>(
    env: &mut Env<'local>,
    _state: &mut WLCState,
    surface: &WlSurface,
    parent: &WlSurface
) -> Result<(), BridgeError> {
    let jsurface = get_java_surface!(surface);
    let jparent = get_java_surface!(parent);

    jsurface.set_parent(env, jparent)?;

    Ok(())
}

pub fn surface_commit(
    state: &mut WLCState,
    surface: &WlSurface
) {
    with_env(|env| _surface_commit(env, state, surface));
}

fn _surface_commit<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    surface: &WlSurface,
) -> Result<(), BridgeError> {
    let jsurface = get_java_surface!(surface);

    // Update children
    let children = get_children(surface);
    let jchildren = get_java_surfaces(env, &children)?;
    jsurface.set_children(env, jchildren)?;

    // Find root surface of this surface tree
    let mut root = surface.clone();
    loop {
        match get_parent(&root) {
            Some(r) => root = r,
            None => break,
        }
    }

    // Update root surface tree
    let jroot = get_java_surface!(&root);
    update_trees(env, &root, jroot)?;
    jroot.calculate_subpos(env)?;

    if is_sync_subsurface(surface) { return Ok(()) }

    // Update surface data from this subsurface tree
    with_surface_tree_upward(
        surface,
        (),
        |_, _, _| TraversalAction::DoChildren(()),
        |surface, data, _| {
            let jsurface = &data.data_map.get::<MySurface>().unwrap().0;
            update_surface_data(env, state, surface, data, jsurface)
                .expect("update_surface_data");
        },
        |_, _, _| true
    );

    // Update root surface framebuffer
    jroot.commit(env)?;

    // If this surface is a toplevel, update its state
    if let Some(toplevel) = toplevel_for_surface(state, surface) {
        toplevel_commit(env, state, &toplevel)?;
    }

    // If this surface is a popup, update its state
    if let Some(popup) = popup_for_surface(state, surface) {
        popup_commit(env, state, &popup)?;
    }

    Ok(())
}

fn update_trees<'local>(
    env: &mut Env<'local>,
    root: &WlSurface,
    jroot: &WLCSurface<'local>,
) -> Result<(), BridgeError> {
    // Set the draw (upward) tree array
    let mut tree_upward: Vec<WlSurface> = vec![];
    with_surface_tree_upward(
        root,
        (),
        |_, _, _| TraversalAction::DoChildren(()),
        |s, _, _| {
            tree_upward.push(s.clone());
        },
        |_, _, _| true
    );
    let jtree_upward = get_java_surfaces(env, &tree_upward)?;
    jroot.set_surface_draw_tree(env, jtree_upward)?;

    // Set the input (downward) tree array
    let mut tree_downward: Vec<WlSurface> = vec![];
    with_surface_tree_downward(
        root,
        (),
        |_, _, _| TraversalAction::DoChildren(()),
        |s, _, _| {
            tree_downward.push(s.clone());
        },
        |_, _, _| true
    );
    let jtree_downward = get_java_surfaces(env, &tree_downward)?;
    jroot.set_surface_input_tree(env, jtree_downward)?;

    Ok(())
}

fn update_surface_data<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    _surface: &WlSurface,
    data: &SurfaceData,
    jsurface: &WLCSurface<'local>,
) -> Result<(), BridgeError> {
    let (sx,sy) = if data.cached_state.has::<SubsurfaceCachedState>() {
        let mut subattr_guard =
            data.cached_state.get::<SubsurfaceCachedState>();
        let subattr = subattr_guard.deref_mut().current();
        (subattr.location.x, subattr.location.y)
    } else {
        (0, 0)
    };

    jsurface.set_xoff(env, sx)?;
    jsurface.set_yoff(env, sy)?;

    let mut attr_guard = data.cached_state.get::<SurfaceAttributes>();
    let attr = attr_guard.deref_mut().current();
    let (maybe_buf, remove_buf) = if let Some(assign) = attr.buffer.take() {
        match assign {
            BufferAssignment::NewBuffer(b) => (Some(b), false),
            BufferAssignment::Removed => (None, true),
        }
    } else {
        (None, false)
    };

    if let Some(buf) = maybe_buf {
        let _result = try_attach_buffer(state, env, jsurface, &buf, data);
        buf.release();
    }

    if remove_buf {
        jsurface.remove_buffer(env)?;
    }

    let mut vp_data_guard = data.cached_state.get::<ViewportCachedState>();
    let vp_data = vp_data_guard.deref_mut().current();

    if let Some(src) = vp_data.src {
        jsurface
            .set_viewport_src(
                env, src.loc.x, src.loc.y, src.size.w, src.size.h,
            )?;
    } else {
        jsurface.unset_viewport_src(env)?;
    }

    if let Some(dst) = vp_data.dst {
        jsurface.set_viewport_dst(env, dst.w, dst.h)?;
    }

    Ok(())
}

#[derive(PartialEq)]
enum BufferAttachResult {
    Success,
    Error,
    NotManaged,
}

fn try_attach_shm(
    _state: &mut WLCState,
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
    _state: &mut WLCState,
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
    state: &mut WLCState,
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

    let tex = bridge::dmabuf::get_dmabuf_java(env, state, dmabuf)
        .expect("get_dmabuf_java");
    jsurface.attach_dmabuf(env, tex).unwrap();

    BufferAttachResult::Success
}

// Proxy to call the try_attach_* family of functions
fn try_attach_buffer(
    state: &mut WLCState,
    env: &mut Env,
    jsurface: &WLCSurface,
    buf: &WlBuffer,
    surf_data: &SurfaceData,
) -> BufferAttachResult {
    let funcs =
        [try_attach_shm, try_attach_single_pixel, try_attach_dmabuf];
    for func in funcs {
        let result = func(state, env, jsurface, buf, surf_data);
        match result {
            BufferAttachResult::NotManaged => continue,
            a => return a,
        }
    }

    unreachable!("Buffer did not match any attachment mechanism!")
}

pub fn surface_from_java_nullable<'local>(
    env: &mut Env<'local>,
    jsurface: &WLCSurface<'local>,
) -> Result<Option<WlSurface>, BridgeError> {
    if jsurface.is_null() {
        return Ok(None);
    }

    let ptr = (jsurface.handle(env)? as usize) as *mut Weak<WlSurface>;
    if ptr.is_null() {
        return Err(BridgeError::SurfaceGone);
    }

    let weak = unsafe { &mut *ptr };
    let surface = weak.upgrade().map_err(|_| BridgeError::SurfaceGone)?;

    Ok(Some(surface))
}

pub fn surface_from_java<'local>(
    env: &mut Env<'local>,
    jsurface: &WLCSurface<'local>,
) -> Result<WlSurface, BridgeError> {
    surface_from_java_nullable(env, jsurface)?.ok_or(BridgeError::SurfaceNull)
}

pub fn send_frame<'local>(
    env: &mut Env<'local>,
    jsurface: WLCSurface<'local>,
) -> Result<(), BridgeError> {
    let surface = surface_from_java(env, &jsurface)?;

    with_states(&surface, |data| {
        let mut attr_guard = data.cached_state.get::<SurfaceAttributes>();
        let attr = attr_guard.deref_mut().current();
        for c in attr.frame_callbacks.drain(..) {
            c.done(get_time());
        }
    });

    Ok(())
}

pub fn input_region_contains<'local>(
    env: &mut Env<'local>,
    jsurface: WLCSurface<'local>,
    x: jdouble,
    y: jdouble,
) -> Result<bool, BridgeError> {
    let surface = match surface_from_java_nullable(env, &jsurface)? {
        Some(s) => s,
        None => {
            return Ok(false);
        },
    };

    let point: Point<f64, Logical> = Point::new(x, y);

    Ok(with_states(&surface, |data| {
        let mut attr_guard = data.cached_state.get::<SurfaceAttributes>();
        let attr = attr_guard.deref_mut().current();
        if let Some(r) = &attr.input_region {
            r.contains(point.to_i32_floor())
        } else {
            true
        }
    }))
}
