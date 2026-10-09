use crate::{
    WLCState,
    bridge::{
        compositor::get_java_surface,
        java_types::{BridgeError, WLCPopup, WLCToplevel},
        utils::{jptr_to_instance, with_env},
    },
};
use jni::{
    Env,
    objects::{Global, JClass, JString},
    sys::{jboolean, jint, jlong},
};
use smithay::{
    reexports::{
        wayland_protocols::xdg::shell::server::{
            xdg_popup::XdgPopup,
            xdg_toplevel::{self, XdgToplevel},
        },
        wayland_server::{
            Resource, Weak,
            protocol::wl_surface::WlSurface,
        },
    },
    utils::Size,
    wayland::{
        compositor::with_states,
        shell::xdg::{
            PopupSurface, SurfaceCachedState, ToplevelSurface,
            XdgToplevelSurfaceData,
        },
    },
};
use std::ops::Deref;
use std::sync::Arc;

pub struct MyToplevelInner(pub Global<WLCToplevel<'static>>);
type MyToplevel = Arc<MyToplevelInner>;

pub fn toplevel_user_data(
    toplevel: &ToplevelSurface
) -> MyToplevel {
    with_states(toplevel.wl_surface(), |data| {
        data
            .data_map
            .get::<MyToplevel>()
            .unwrap()
            .clone()
    })
}

// Turns &ToplevelSurface into &WLCToplevel
#[macro_export]
macro_rules! get_java_toplevel {
    ($toplevel:expr) => {
        (&$crate::bridge::shell::toplevel_user_data($toplevel).0)
    };
}
pub use get_java_toplevel;

pub struct MyPopupInner(pub Global<WLCPopup<'static>>);
type MyPopup = Arc<MyPopupInner>;

pub fn popup_user_data(
    popup: &PopupSurface
) -> MyPopup {
    with_states(popup.wl_surface(), |data| {
        data
            .data_map
            .get::<MyPopup>()
            .unwrap()
            .clone()
    })
}

// Turns &PopupSurface into &WLCPopup
#[macro_export]
macro_rules! get_java_popup {
    ($popup:expr) => {
        (&$crate::bridge::shell::popup_user_data($popup).0)
    };
}
pub use get_java_popup;

pub fn new_toplevel(state: &mut WLCState, toplevel: &ToplevelSurface) {
    with_env(|env| _new_toplevel(env, state, toplevel))
}

fn _new_toplevel<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    toplevel: &ToplevelSurface
) -> Result<(), BridgeError> {
    // Create handle from boxed XdgToplevel weak reference
    let weak: Weak<XdgToplevel> = toplevel.xdg_toplevel().downgrade();
    let weak = Box::new(weak);
    let ptr = (Box::into_raw(weak) as usize) as jlong;

    let jsurface = get_java_surface!(toplevel.wl_surface());

    // Create java toplevel and insert global reference into toplevel root
    // wl_surface data
    let jtoplevel = WLCToplevel::new(env, ptr, jsurface)?;
    let jtoplevel_ref = env.new_global_ref(&jtoplevel)?;
    let my_toplevel = Arc::new(MyToplevelInner(jtoplevel_ref));

    with_states(toplevel.wl_surface(), |data| {
        data.data_map.insert_if_missing(|| my_toplevel);
    });

    jtoplevel.set_surface(env, jsurface)?;

    // Tell the java code about the new toplevel
    state.bridge.java.add_toplevel(env, jtoplevel)?;

    Ok(())
}

pub fn toplevel_destroyed(state: &mut WLCState, toplevel: &ToplevelSurface) {
    with_env(|env| _toplevel_destroyed(env, state, toplevel))
}

fn _toplevel_destroyed<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    toplevel: &ToplevelSurface
) -> Result<(), BridgeError> {
    // Remove toplevel in java bridge code
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.delete_toplevel(env, jtoplevel)?;

    // Remove and destroy its handle
    let ptr = (jtoplevel.handle(env)? as usize) as *mut Weak<XdgToplevel>;
    let ptr = unsafe { Box::from_raw(ptr) };
    drop(ptr);
    jtoplevel.set_handle(env, 0)?;

    // The java global reference will get dropped with the toplevel

    Ok(())
}

pub fn toplevel_for_surface(
    state: &mut WLCState,
    surface: &WlSurface,
) -> Option<ToplevelSurface> {
    state
        .xdg_state
        .toplevel_surfaces()
        .iter()
        .find(|t| t.wl_surface() == surface)
        .cloned()
}

pub fn toplevel_commit<'local>(
    env: &mut Env<'local>,
    _state: &mut WLCState,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    let geometry = with_states(toplevel.wl_surface(), |states| {
        let mut guard = states.cached_state.get::<SurfaceCachedState>();
        guard
            .current()
            .geometry
            .clone()
    });

    if let Some(geometry) = geometry {
        jtoplevel.update_geometry(
            env,
            geometry.loc.x,
            geometry.loc.y,
            geometry.size.w,
            geometry.size.h,
        )?;
    } else {
        jtoplevel.default_geometry(env)?;
    }

    Ok(())
}

pub fn toplevel_from_java_nullable<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    jtoplevel: &WLCToplevel<'local>,
) -> Result<Option<ToplevelSurface>, BridgeError> {
    if jtoplevel.is_null() {
        return Ok(None);
    }

    let ptr = (jtoplevel.handle(env)? as usize) as *mut Weak<XdgToplevel>;
    if ptr.is_null() {
        return Err(BridgeError::ToplevelGone);
    }

    let weak = unsafe { &mut *ptr };
    let xdg_toplevel = weak.upgrade().map_err(|_| BridgeError::ToplevelGone)?;
    let toplevel = state.xdg_state.get_toplevel(&xdg_toplevel).unwrap();

    return Ok(Some(toplevel));
}

pub fn toplevel_from_java<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    jtoplevel: &WLCToplevel<'local>,
) -> Result<ToplevelSurface, BridgeError> {
    toplevel_from_java_nullable(env, state, jtoplevel)?
        .ok_or(BridgeError::ToplevelNull)
}

pub fn popup_from_java_nullable<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    jpopup: &WLCPopup<'local>,
) -> Result<Option<PopupSurface>, BridgeError> {
    if jpopup.is_null() {
        return Ok(None);
    }

    let ptr = (jpopup.handle(env)? as usize) as *mut Weak<XdgPopup>;
    if ptr.is_null() {
        return Err(BridgeError::PopupGone);
    }

    let weak = unsafe { &mut *ptr };
    let xdg_popup = weak.upgrade().map_err(|_| BridgeError::PopupGone)?;
    let popup = state.xdg_state.get_popup(&xdg_popup).unwrap();

    return Ok(Some(popup));
}

#[allow(unused)]
pub fn popup_from_java<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    jpopup: &WLCPopup<'local>,
) -> Result<PopupSurface, BridgeError> {
    popup_from_java_nullable(env, state, jpopup)?
        .ok_or(BridgeError::PopupNull)
}

pub fn new_popup(state: &mut WLCState, popup: &PopupSurface) {
    with_env(|env| _new_popup(env, state, popup))
}

fn _new_popup<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    popup: &PopupSurface
) -> Result<(), BridgeError> {
    // Create handle from boxed XdgPopup weak reference
    let weak: Weak<XdgPopup> = popup.xdg_popup().downgrade();
    let weak = Box::new(weak);
    let ptr = (Box::into_raw(weak) as usize) as jlong;

    let jsurface = get_java_surface!(popup.wl_surface());

    // Create java popup and insert global reference into popup root
    // wl_surface data
    let jpopup = WLCPopup::new(env, ptr, jsurface)?;
    let jpopup_ref = env.new_global_ref(&jpopup)?;
    let my_popup = Arc::new(MyPopupInner(jpopup_ref));

    with_states(popup.wl_surface(), |data| {
        data.data_map.insert_if_missing(|| my_popup);
    });

    jpopup.set_surface(env, jsurface)?;

    // Find and set parent toplevel or popup
    if let Some(parent_surf) = popup.get_parent_surface() {
        if let Some(parent) = popup_for_surface(state, &parent_surf) {
            let jparent = get_java_popup!(&parent);
            jpopup.set_parent(env, jparent)?;
        } else if let Some(parent) = toplevel_for_surface(state, &parent_surf) {
            let jparent = get_java_toplevel!(&parent);
            jpopup.set_parent(env, jparent)?;
        }
    }

    // Tell the java code about the new popup
    state.bridge.java.add_popup(env, jpopup)?;

    Ok(())
}

pub fn popup_destroyed(state: &mut WLCState, popup: &PopupSurface) {
    with_env(|env| _popup_destroyed(env, state, popup))
}

fn _popup_destroyed<'local>(
    env: &mut Env<'local>,
    state: &mut WLCState,
    popup: &PopupSurface
) -> Result<(), BridgeError> {
    // Remove popup in java bridge code
    let jpopup = get_java_popup!(popup);
    state.bridge.java.delete_popup(env, jpopup)?;

    // Remove and destroy its handle
    let ptr = (jpopup.handle(env)? as usize) as *mut Weak<XdgPopup>;
    let ptr = unsafe { Box::from_raw(ptr) };
    drop(ptr);
    jpopup.set_handle(env, 0)?;

    // The java global reference will get dropped with the popup

    Ok(())
}

pub fn popup_for_surface(
    state: &mut WLCState,
    surface: &WlSurface,
) -> Option<PopupSurface> {
    state
        .xdg_state
        .popup_surfaces()
        .iter()
        .find(|t| t.wl_surface() == surface)
        .cloned()
}

pub fn popup_commit<'local>(
    env: &mut Env<'local>,
    _state: &mut WLCState,
    popup: &PopupSurface,
) -> Result<(), BridgeError> {
    let jpopup = get_java_popup!(popup);
    let geometry = with_states(popup.wl_surface(), |states| {
        let mut guard = states.cached_state.get::<SurfaceCachedState>();
        guard
            .current()
            .geometry
            .clone()
    });

    if let Some(geometry) = geometry {
        jpopup.update_geometry(
            env,
            geometry.loc.x,
            geometry.loc.y,
            geometry.size.w,
            geometry.size.h,
        )?;
    } else {
        jpopup.default_geometry(env)?;
    }

    let offset = popup.with_committed_state(|state| {
        state.map(|s| s.geometry.loc)
    });

    if let Some(offset) = offset {
        jpopup.set_offset_x(env, offset.x)?;
        jpopup.set_offset_y(env, offset.y)?;
    }

    Ok(())
}

pub fn toplevel_resize<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    jtoplevel: WLCToplevel<'local>,
    width: jint,
    height: jint,
    interactive: jboolean,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let toplevel = toplevel_from_java(env, &mut instance.state, &jtoplevel)?;

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
    jtoplevel.set_fullscreen(env, false)?;

    toplevel.send_pending_configure();

    Ok(())
}

pub fn toplevel_resize_ovr<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    jtoplevel: WLCToplevel<'local>,
    width: jint,
    height: jint,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let toplevel = toplevel_from_java(env, &mut instance.state, &jtoplevel)?;

    toplevel.with_pending_state(|state| {
        state.size = Some(Size::new(width, height));
        state.states.unset(xdg_toplevel::State::Resizing);
    });

    toplevel.send_pending_configure();

    Ok(())
}

pub fn toplevel_maximize<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    jtoplevel: WLCToplevel<'local>,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let toplevel = toplevel_from_java(env, &mut instance.state, &jtoplevel)?;

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

pub fn toplevel_fullscreen<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    instance: jlong,
    jtoplevel: WLCToplevel<'local>,
) -> Result<(), BridgeError> {
    let instance = jptr_to_instance!(instance)?;
    let toplevel = toplevel_from_java(env, &mut instance.state, &jtoplevel)?;

    toplevel.with_pending_state(|state| {
        let output = &instance.state.output;
        state.size = Some(output.size());
        state.states.set(xdg_toplevel::State::Fullscreen);
    });

    jtoplevel.set_fullscreen(env, true)?;

    toplevel.send_configure();
    Ok(())
}

pub fn toplevel_update_app_id(
    toplevel: &ToplevelSurface,
) {
    with_env(|env| _toplevel_update_app_id(env, toplevel))
}

fn _toplevel_update_app_id(
    env: &mut Env,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let app_id = with_states(toplevel.wl_surface(), |data| {
        data
            .data_map
            .get::<XdgToplevelSurfaceData>()
            .unwrap()
            .lock()
            .unwrap()
            .deref()
            .app_id
            .clone()
    });

    let jtoplevel = get_java_toplevel!(toplevel);

    if let Some(app_id) = app_id {
        let app_id = env.new_string(app_id)?;
        jtoplevel.set_app_id(env, app_id)?;
    } else {
        jtoplevel.set_app_id(env, JString::null())?;
    }

    Ok(())
}

pub fn toplevel_update_title(
    toplevel: &ToplevelSurface,
) {
    with_env(|env| _toplevel_update_title(env, toplevel))
}

fn _toplevel_update_title(
    env: &mut Env,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let title = with_states(toplevel.wl_surface(), |data| {
        data
            .data_map
            .get::<XdgToplevelSurfaceData>()
            .unwrap()
            .lock()
            .unwrap()
            .deref()
            .title
            .clone()
    });

    let jtoplevel = get_java_toplevel!(toplevel);

    if let Some(title) = title {
        let title = env.new_string(title)?;
        jtoplevel.set_title(env, title)?;
    } else {
        jtoplevel.set_title(env, JString::null())?;
    }

    Ok(())
}

pub fn on_toplevel_maximize(
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) {
    with_env(|env| _on_toplevel_maximize(env, state, toplevel))
}

fn _on_toplevel_maximize(
    env: &mut Env,
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.call_on_maximize(env, jtoplevel)?;
    Ok(())
}

pub fn on_toplevel_unmaximize(
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) {
    with_env(|env| _on_toplevel_unmaximize(env, state, toplevel))
}

fn _on_toplevel_unmaximize(
    env: &mut Env,
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.call_on_unmaximize(env, jtoplevel)?;
    Ok(())
}

pub fn on_toplevel_fullscreen(
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) {
    with_env(|env| _on_toplevel_fullscreen(env, state, toplevel))
}

fn _on_toplevel_fullscreen(
    env: &mut Env,
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.call_on_fullscreen(env, jtoplevel)?;
    Ok(())
}

pub fn on_toplevel_unfullscreen(
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) {
    with_env(|env| _on_toplevel_unfullscreen(env, state, toplevel))
}

fn _on_toplevel_unfullscreen(
    env: &mut Env,
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.call_on_unfullscreen(env, jtoplevel)?;
    Ok(())
}

pub fn on_toplevel_minimize(
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) {
    with_env(|env| _on_toplevel_minimize(env, state, toplevel))
}

fn _on_toplevel_minimize(
    env: &mut Env,
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.call_on_minimize(env, jtoplevel)?;
    Ok(())
}

pub fn on_toplevel_move(
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
    serial: u32,
) {
    with_env(|env| _on_toplevel_move(env, state, toplevel, serial))
}

fn _on_toplevel_move(
    env: &mut Env,
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
    serial: u32,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.call_on_move(env, jtoplevel, serial as jint)?;
    Ok(())
}

pub fn on_toplevel_resize(
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
    serial: u32,
    edges: u32,
) {
    with_env(|env| _on_toplevel_resize(env, state, toplevel, serial, edges))
}

fn _on_toplevel_resize(
    env: &mut Env,
    state: &mut WLCState,
    toplevel: &ToplevelSurface,
    serial: u32,
    edges: u32,
) -> Result<(), BridgeError> {
    let jtoplevel = get_java_toplevel!(toplevel);
    state.bridge.java.call_on_resize(
        env,
        jtoplevel,
        serial as jint,
        edges as jint
    )?;
    Ok(())
}
