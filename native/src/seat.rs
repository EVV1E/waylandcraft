use crate::WLCState;
use crate::utils::{get_time, new_serial, to_fixed2};
use smithay::{
    reexports::{
        wayland_protocols::wp::cursor_shape::v1::server::{
            wp_cursor_shape_device_v1,
            wp_cursor_shape_device_v1::WpCursorShapeDeviceV1,
            wp_cursor_shape_manager_v1,
            wp_cursor_shape_manager_v1::WpCursorShapeManagerV1,
        },
        wayland_protocols::wp::pointer_constraints::zv1::server::{
            zwp_confined_pointer_v1 as zwp_confined,
            zwp_confined_pointer_v1::ZwpConfinedPointerV1,
            zwp_locked_pointer_v1 as zwp_locked,
            zwp_locked_pointer_v1::ZwpLockedPointerV1,
            zwp_pointer_constraints_v1 as zwp_constraints,
            zwp_pointer_constraints_v1::ZwpPointerConstraintsV1,
        },
        wayland_protocols::wp::relative_pointer::zv1::server::{
            zwp_relative_pointer_manager_v1 as zwp_rpm,
            zwp_relative_pointer_manager_v1::ZwpRelativePointerManagerV1,
            zwp_relative_pointer_v1 as zwp_relpointer,
            zwp_relative_pointer_v1::ZwpRelativePointerV1,
        },
        wayland_server::{
            Client, DataInit, Dispatch, DisplayHandle, GlobalDispatch, New,
            Resource,
            backend::ClientId,
            protocol::{
                wl_keyboard::{self, KeyState, KeymapFormat, WlKeyboard},
                wl_pointer::{self, Axis, AxisSource, ButtonState, WlPointer},
                wl_seat::{self, WlSeat},
                wl_surface::WlSurface,
            },
        },
    },
    utils::SealedFile,
};
use std::collections::HashSet;
use std::ffi::CString;
use std::ops::DerefMut;
use std::os::fd::AsFd;
use std::sync::{Arc, Mutex};
use xkbcommon::xkb::{self, Keymap};

pub struct WLCSeatState {
    pub pointers: Vec<WlPointer>,
    pub keyboards: Vec<WlKeyboard>,
    pub kb_active: bool,
    pub pressed_keys: HashSet<u32>,
    pub keymap: Keymap,
    pub keymap_file: SealedFile,
    pub xkb_context: xkb::Context,
    pub xkb_state: xkb::State,
    pub cursor_shape: Option<u32>,
}

pub struct WLCPointerData {
    // WlSurface holding pointer focus
    // This surface has to be of the same client as the WlPointer
    focus: Option<WlSurface>,
    // Value of current pointer focus enter serial
    last_enter: Option<u32>,
    // Value of last motion event wl_fixed
    last_motion: Option<(i32, i32)>,
    // Relative pointer objects
    relative_pointers: Vec<ZwpRelativePointerV1>,
    // Pointer position lock
    lock: Option<WLCPointerLock>,
    // Pointer confined surface
    confined: Option<WlSurface>,
}

type WLCPointer = Arc<Mutex<WLCPointerData>>;

pub struct WLCCursorShapeDeviceData {
    pointer: Option<WlPointer>,
}

type WLCCursorShapeDevice = Arc<Mutex<WLCCursorShapeDeviceData>>;

pub struct WLCPointerLock {
    locked_pointer: ZwpLockedPointerV1,
    surface: WlSurface,
    active: bool, // Activated event sent
}

pub struct WLCKeyboardData {
    // WlSurface holding keyboard focus
    // This surface has to be of the same client as the WlKeyboard
    focus: Option<WlSurface>,
}

type WLCKeyboard = Arc<Mutex<WLCKeyboardData>>;

// Keyboard RMLVO keymap specifier
#[allow(clippy::upper_case_acronyms)]
#[derive(Default)]
pub struct RMLVO {
    pub rules: String,
    pub model: String,
    pub layout: String,
    pub variant: String,
    pub options: String,
}

fn with_pointer_data<F, R>(pointer: &WlPointer, f: F) -> R
where
    F: FnOnce(&mut WLCPointerData) -> R,
{
    let mut guard = pointer.data::<WLCPointer>().unwrap().lock().unwrap();
    let data = guard.deref_mut();
    f(data)
}

fn with_cursor_shape_device_data<F, R>(
    device: &WpCursorShapeDeviceV1,
    f: F,
) -> R
where
    F: FnOnce(&mut WLCCursorShapeDeviceData) -> R,
{
    let mut guard = device
        .data::<WLCCursorShapeDevice>()
        .unwrap()
        .lock()
        .unwrap();
    let data = guard.deref_mut();
    f(data)
}

fn with_keyboard_data<F>(keyboard: &WlKeyboard, f: F)
where
    F: FnOnce(&mut WLCKeyboardData),
{
    let mut guard = keyboard.data::<WLCKeyboard>().unwrap().lock().unwrap();
    let data = guard.deref_mut();
    f(data);
}

fn create_keymap_file(keymap: &Keymap) -> SealedFile {
    let keymap_str = keymap.get_as_string(xkb::KEYMAP_FORMAT_TEXT_V1);
    SealedFile::with_content(
        c"waylandcraft-keymap",
        &CString::new(keymap_str.as_str()).unwrap(),
    )
    .expect("SealedFile create")
}

impl WLCSeatState {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Self {
        let xkb_context = xkb::Context::new(xkb::CONTEXT_NO_FLAGS);
        let keymap = Keymap::new_from_names(
            &xkb_context,
            "",                           // rules
            "",                           // model
            "",                           // layout
            "",                           // variant
            None,                         // options
            xkb::KEYMAP_COMPILE_NO_FLAGS, // flags
        )
        .expect("default keymap create");

        let xkb_state = xkb::State::new(&keymap);
        let keymap_file = create_keymap_file(&keymap);

        WLCSeatState {
            pointers: vec![],
            keyboards: vec![],
            kb_active: false,
            pressed_keys: HashSet::new(),
            keymap,
            keymap_file,
            xkb_context,
            xkb_state,
            cursor_shape: None,
        }
    }

    pub fn create_globals(&self, disp: &DisplayHandle) {
        disp.create_global::<WLCState, WlSeat, ()>(8, ());
        disp.create_global::<WLCState, ZwpRelativePointerManagerV1, ()>(1, ());
        disp.create_global::<WLCState, ZwpPointerConstraintsV1, ()>(1, ());
        disp.create_global::<WLCState, WpCursorShapeManagerV1, ()>(2, ());
    }

    fn pointer_frame(&self, pointer: &WlPointer) {
        if pointer.version() >= wl_pointer::EVT_FRAME_SINCE {
            pointer.frame();
        }
    }

    fn pointer_focus_eq(
        &self,
        pointer: &WLCPointerData,
        surface: &WlSurface,
    ) -> bool {
        pointer.focus.as_ref().is_some_and(|s| s == surface)
    }

    fn pointer_focus(&mut self, surface: Option<&WlSurface>, x: f64, y: f64) {
        let serial = new_serial();

        // Unfocus any pointers currently focused on the wrong surface
        self.for_all_pointers(|pointer, data| {
            let focus = match &data.focus {
                Some(s) => s,
                None => return,
            };
            let unfocus = match surface {
                Some(s) => s != focus,
                None => true,
            };
            if unfocus {
                pointer.leave(serial, focus);
                self.pointer_frame(pointer);
                data.focus = None;
                data.last_enter = None;
                data.last_motion = None;
            }
        });

        let surface = match surface {
            Some(s) => s,
            None => return,
        };

        // Generate pointer enter events
        self.for_all_pointers(|pointer, data| {
            // Already correct focus
            if self.pointer_focus_eq(data, surface) {
                return;
            }
            assert_eq!(data.focus, None);

            // Client does not own surface
            if surface.client() != pointer.client() {
                return;
            }

            pointer.enter(serial, surface, x, y);
            self.pointer_frame(pointer);
            data.focus = Some(surface.clone());
            data.last_enter = Some(serial);
            data.last_motion = None;
        });
    }

    // Focus the pointer on the given surface and register movement
    pub fn pointer_motion_focus(
        &mut self,
        surface: Option<&WlSurface>,
        x: f64,
        y: f64,
    ) {
        let surface = surface.filter(|s| s.is_alive());

        self.pointer_focus(surface, x, y);
        if surface.is_none() {
            return;
        }

        self.pointer_motion(x, y);
    }

    // Send motion events
    pub fn pointer_motion(&mut self, x: f64, y: f64) {
        let time = get_time();
        let pos: (i32, i32) = to_fixed2(x, y);
        self.for_all_pointers(|pointer, data| {
            // Pointer does not hold focus
            if data.focus.is_none() {
                return;
            }
            // Pointer location did not change
            if data.last_motion == Some(pos) {
                return;
            }

            pointer.motion(time, x, y);
            self.pointer_frame(pointer);
            data.last_motion = Some(pos);
        });
    }

    // Emit relative movement on the surface with active pointer focus
    pub fn pointer_relative_motion(&self, dx: f64, dy: f64) {
        self.for_all_pointers(|pointer, data| {
            if data.focus.is_none() {
                return;
            }
            for relative_pointer in &data.relative_pointers {
                let time = (get_time() as u64) * 1000; // ms to µs
                relative_pointer.relative_motion(
                    (time >> 32) as u32,        // utime_hi
                    (time & 0xffffffff) as u32, // utime_lo
                    dx,                         // dx
                    dy,                         // dy
                    dx,                         // dx_unaccel
                    dy,                         // dy_unaccel
                );
            }
            self.pointer_frame(pointer);
        });
    }

    pub fn pointer_button(&mut self, button: u32, state: ButtonState) -> u32 {
        let serial = new_serial();
        self.for_all_pointers(|pointer, data| {
            if data.focus.is_none() {
                return;
            }

            pointer.button(serial, get_time(), button, state);
            self.pointer_frame(pointer);
        });
        serial
    }

    pub fn pointer_axis(&self, axis: Axis, value: f64) {
        let val120 = (value * 120.0).floor() as i32;
        if val120 == 0 { return }

        self.for_all_pointers(|pointer, data| {
            if data.focus.is_some() {
                let version = pointer.version();
                if version >= wl_pointer::EVT_AXIS_SOURCE_SINCE {
                    pointer.axis_source(AxisSource::Wheel);
                }
                if version >= wl_pointer::EVT_AXIS_VALUE120_SINCE {
                    pointer.axis_value120(axis, val120);
                } else if version >= wl_pointer::EVT_AXIS_DISCRETE_SINCE {
                    pointer.axis_discrete(axis, value.floor() as i32);
                }
                pointer.axis(get_time(), axis, value * 10.0);
                self.pointer_frame(pointer);
            }
        });
    }

    pub fn keyboard_update_xkb(&mut self, key: u32, pressed: bool) {
        let dir = match pressed {
            true => xkb::KeyDirection::Down,
            false => xkb::KeyDirection::Up,
        };
        let code = xkb::Keycode::new(key);
        self.xkb_state.update_key(code, dir);

        if pressed {
            self.pressed_keys.insert(key);
        } else {
            self.pressed_keys.remove(&key);
        }
    }

    pub fn keyboard_focus(&mut self, surface: WlSurface) {
        if !surface.is_alive() {
            return;
        };
        let client = surface.client().unwrap();
        let serial = new_serial();

        self.for_all_keyboards(|keyboard, data| {
            let keyboard_client = keyboard.client().unwrap();

            // If WlKeyboard belongs to different client, make it lose focus
            if keyboard_client != client {
                if let Some(focus) = &data.focus {
                    keyboard.leave(serial, focus);
                    data.focus = None;
                }
                return;
            }

            // This keyboard is now guaranteed to be of the same client as the
            // surface

            if let Some(focus) = &data.focus {
                if *focus == surface {
                    // Surface already focused
                    return;
                }
                keyboard.leave(serial, focus);
                data.focus = None;
            }

            // Keyboard should enter surface
            let pressed = self.serialize_pressed_keys();

            keyboard.enter(serial, &surface, pressed);
            data.focus = Some(surface.clone());

            self.send_modifiers(keyboard, serial);
        });
    }

    fn serialize_pressed_keys(&self) -> Vec<u8> {
        let mut pressed: Vec<u32> = vec![];
        if self.kb_active {
            pressed = self.pressed_keys.iter().copied().collect();
        }

        let pressed: Vec<u8> =
            pressed.iter().flat_map(|&k| k.to_ne_bytes()).collect();

        pressed
    }

    fn keyboard_refocus(&mut self) {
        let serial = new_serial();
        self.for_all_keyboards(|keyboard, data| {
            if let Some(focus) = &data.focus {
                if !focus.is_alive() {
                    return;
                }

                let pressed = self.serialize_pressed_keys();
                keyboard.leave(serial, focus);
                keyboard.enter(serial, focus, pressed);
                self.send_modifiers(keyboard, serial);
            }
        });
    }

    pub fn activate_keyboard(&mut self) {
        if self.kb_active {
            return;
        }

        self.kb_active = true;
        self.keyboard_refocus();
    }

    pub fn deactivate_keyboard(&mut self) {
        if !self.kb_active {
            return;
        }

        self.kb_active = false;
        self.keyboard_refocus();
    }

    fn send_modifiers(&self, keyboard: &WlKeyboard, serial: u32) {
        if !self.kb_active {
            keyboard.modifiers(
                serial,
                0, // MODS_DEPRESSED
                0, // MODS_LATCHED
                0, // MODS_LOCKED
                self.xkb_state.serialize_layout(xkb::STATE_LAYOUT_EFFECTIVE),
            );
            return;
        }
        keyboard.modifiers(
            serial,
            self.xkb_state.serialize_mods(xkb::STATE_MODS_DEPRESSED),
            self.xkb_state.serialize_mods(xkb::STATE_MODS_LATCHED),
            self.xkb_state.serialize_mods(xkb::STATE_MODS_LOCKED),
            self.xkb_state.serialize_layout(xkb::STATE_LAYOUT_EFFECTIVE),
        );
    }

    pub fn keyboard_unfocus(&mut self) {
        let serial = new_serial();
        self.for_all_keyboards(|keyboard, data| {
            if let Some(focus) = &data.focus {
                keyboard.leave(serial, focus);
                data.focus = None;
            }
        });
    }

    pub fn keyboard_key(&self, key: u32, state: KeyState) {
        if !self.kb_active {
            return;
        }
        let serial = new_serial();
        self.for_all_keyboards(|keyboard, data| {
            if data.focus.is_some() {
                keyboard.key(serial, get_time(), key - 8, state);
                self.send_modifiers(keyboard, serial);
            }
        });
    }

    pub fn pointer_unlock(&self) {
        self.for_all_pointers(|_pointer, data| {
            if let Some(lock) = &mut data.lock {
                if lock.active {
                    lock.locked_pointer.unlocked();
                }
                lock.active = false;
            }
        });
    }

    pub fn pointer_lock(&self, surface: &WlSurface) -> bool {
        for pointer in &self.pointers {
            let mut locked = false;
            with_pointer_data(pointer, |data| {
                if let Some(lock) = &mut data.lock {
                    if lock.surface == *surface {
                        if !lock.active {
                            lock.locked_pointer.locked();
                            lock.active = true;
                        }
                        locked = true;
                    } else if lock.active {
                        lock.locked_pointer.unlocked();
                        lock.active = false;
                    }
                }
            });

            if locked {
                return true;
            }
        }
        false
    }

    fn for_all_pointers<F>(&self, mut f: F)
    where
        F: FnMut(&WlPointer, &mut WLCPointerData),
    {
        for pointer in &self.pointers {
            with_pointer_data(pointer, |data| f(pointer, data));
        }
    }

    fn for_all_keyboards<F>(&self, mut f: F)
    where
        F: FnMut(&WlKeyboard, &mut WLCKeyboardData),
    {
        for keyboard in &self.keyboards {
            with_keyboard_data(keyboard, |data| f(keyboard, data));
        }
    }

    fn change_keymap(&mut self, keymap: Keymap) {
        let xkb_state = xkb::State::new(&keymap);
        let keymap_file = create_keymap_file(&keymap);

        self.xkb_state = xkb_state;
        self.keymap = keymap;
        self.keymap_file = keymap_file;
        self.keyboard_refocus();
    }

    pub fn change_keymap_to_default(&mut self) {
        let keymap = Keymap::new_from_names(
            &self.xkb_context,
            "",                           // rules
            "",                           // model
            "",                           // layout
            "",                           // variant
            None,                         // options
            xkb::KEYMAP_COMPILE_NO_FLAGS, // flags
        )
        .expect("default keymap create");
        self.change_keymap(keymap);
    }

    pub fn change_keymap_to_desc(&mut self, desc: &RMLVO) -> bool {
        let keymap = Keymap::new_from_names(
            &self.xkb_context,
            &desc.rules,
            &desc.model,
            &desc.layout,
            &desc.variant,
            Some(desc.options.clone()),
            xkb::KEYMAP_COMPILE_NO_FLAGS, // flags
        );
        let keymap = match keymap {
            Some(k) => k,
            None => return false,
        };
        self.change_keymap(keymap);
        true
    }

    pub fn change_keymap_from_str(&mut self, desc: String) -> bool {
        let keymap = Keymap::new_from_string(
            &self.xkb_context,
            desc,
            xkb::KEYMAP_FORMAT_TEXT_V1,
            xkb::KEYMAP_COMPILE_NO_FLAGS,
        );
        let keymap = match keymap {
            Some(k) => k,
            None => return false,
        };
        self.change_keymap(keymap);
        true
    }

    pub fn export_keymap(&self) -> String {
        self.keymap.get_as_string(xkb::KEYMAP_FORMAT_TEXT_V1)
    }
}

impl GlobalDispatch<WlSeat, ()> for WLCState {
    fn bind(
        _state: &mut Self,
        _handle: &DisplayHandle,
        _client: &Client,
        resource: New<WlSeat>,
        _data: &(),
        data_init: &mut DataInit<'_, Self>,
    ) {
        let seat: WlSeat = data_init.init(resource, ());
        if seat.version() >= wl_seat::EVT_NAME_SINCE {
            seat.name("waylandcraft-seat".into());
        }

        let mut caps: wl_seat::Capability = wl_seat::Capability::empty();
        caps.insert(wl_seat::Capability::Pointer);
        caps.insert(wl_seat::Capability::Keyboard);
        seat.capabilities(caps);
    }
}

impl Dispatch<WlSeat, ()> for WLCState {
    fn request(
        state: &mut Self,
        _client: &Client,
        seat_resource: &WlSeat,
        request: wl_seat::Request,
        _data: &(),
        _disp: &DisplayHandle,
        data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_seat::Request::GetPointer { id } => {
                let pointer_data = WLCPointerData {
                    focus: None,
                    last_enter: None,
                    last_motion: None,
                    relative_pointers: vec![],
                    lock: None,
                    confined: None,
                };
                let pointer_data = Arc::new(Mutex::new(pointer_data));

                let pointer: WlPointer =
                    data_init.init(id, pointer_data.clone());

                state.seat.pointers.push(pointer);
            }
            wl_seat::Request::GetKeyboard { id } => {
                let keyboard_data = WLCKeyboardData { focus: None };
                let keyboard_data = Arc::new(Mutex::new(keyboard_data));

                let keyboard: WlKeyboard =
                    data_init.init(id, keyboard_data.clone());

                state.seat.keyboards.push(keyboard.clone());

                let keymap = &state.seat.keymap_file;
                keyboard.keymap(
                    KeymapFormat::XkbV1,
                    keymap.as_fd(),
                    keymap.size() as u32,
                );

                if keyboard.version() >= wl_keyboard::EVT_REPEAT_INFO_SINCE {
                    keyboard.repeat_info(25, 600);
                }
            }
            _ => {
                seat_resource.post_error(
                    wl_seat::Error::MissingCapability,
                    "accessed missing seat capability",
                );
            }
        }
    }
}

impl Dispatch<WlPointer, WLCPointer> for WLCState {
    fn request(
        state: &mut Self,
        _client: &Client,
        pointer: &WlPointer,
        request: wl_pointer::Request,
        _data: &WLCPointer,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_pointer::Request::SetCursor {
                serial, surface, ..
            } => {
                let last_enter =
                    with_pointer_data(pointer, |data| data.last_enter);
                if last_enter.is_none() {
                    return;
                }
                if last_enter.unwrap() != serial {
                    return;
                }

                if surface.is_none() {
                    // Attaching an empty surface to hide cursor
                    // Zero value (not defined in protocol) means hidden here.
                    state.seat.cursor_shape = Some(0);
                } else {
                    // When an image is attached instead of a shape, reset to
                    // default because this compositor doesn't implement normal
                    // surface-based cursors, only cursor-shape.
                    state.seat.cursor_shape = None;
                }
            }
            wl_pointer::Request::Release => {}
            _ => unreachable!(),
        }
    }

    fn destroyed(
        state: &mut Self,
        _client: ClientId,
        pointer_resource: &WlPointer,
        _data: &WLCPointer,
    ) {
        state.seat.pointers.retain(|p| p != pointer_resource);
    }
}

impl Dispatch<WlKeyboard, WLCKeyboard> for WLCState {
    fn request(
        _state: &mut Self,
        _client: &Client,
        _keyboard_resource: &WlKeyboard,
        request: wl_keyboard::Request,
        _data: &WLCKeyboard,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_keyboard::Request::Release => {}
            _ => unreachable!(),
        }
    }

    fn destroyed(
        state: &mut Self,
        _client: ClientId,
        keyboard_resource: &WlKeyboard,
        _data: &WLCKeyboard,
    ) {
        state.seat.keyboards.retain(|kb| kb != keyboard_resource);
    }
}

impl GlobalDispatch<ZwpRelativePointerManagerV1, ()> for WLCState {
    fn bind(
        _state: &mut Self,
        _handle: &DisplayHandle,
        _client: &Client,
        resource: New<ZwpRelativePointerManagerV1>,
        _data: &(),
        data_init: &mut DataInit<'_, Self>,
    ) {
        data_init.init(resource, ());
    }
}

impl Dispatch<ZwpRelativePointerManagerV1, ()> for WLCState {
    fn request(
        _state: &mut Self,
        _client: &Client,
        _manager_resource: &ZwpRelativePointerManagerV1,
        request: zwp_rpm::Request,
        _data: &(),
        _disp: &DisplayHandle,
        data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            zwp_rpm::Request::Destroy => {}
            zwp_rpm::Request::GetRelativePointer { id, pointer } => {
                let relative_pointer = data_init.init(id, ());

                with_pointer_data(&pointer, |data| {
                    data.relative_pointers.push(relative_pointer);
                });
            }
            _ => unreachable!(),
        }
    }
}

impl Dispatch<ZwpRelativePointerV1, ()> for WLCState {
    fn request(
        _state: &mut Self,
        _client: &Client,
        _relpointer_resource: &ZwpRelativePointerV1,
        request: zwp_relpointer::Request,
        _data: &(),
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            zwp_relpointer::Request::Destroy => {}
            _ => unreachable!(),
        }
    }

    fn destroyed(
        state: &mut Self,
        _client: ClientId,
        relpointer_resource: &ZwpRelativePointerV1,
        _data: &(),
    ) {
        state.seat.for_all_pointers(|_pointer, data| {
            data.relative_pointers.retain(|r| r != relpointer_resource);
        });
    }
}

impl GlobalDispatch<ZwpPointerConstraintsV1, ()> for WLCState {
    fn bind(
        _state: &mut Self,
        _handle: &DisplayHandle,
        _client: &Client,
        resource: New<ZwpPointerConstraintsV1>,
        _data: &(),
        data_init: &mut DataInit<'_, Self>,
    ) {
        data_init.init(resource, ());
    }
}

fn has_existing_constraint(
    state: &mut WLCState,
    pointer: &WlPointer,
    surface: &WlSurface,
) -> bool {
    let mut err = false;
    with_pointer_data(pointer, |data| {
        if data.lock.is_some() || data.confined.is_some() {
            err = true;
        }
    });
    state.seat.for_all_pointers(|_pointer, data| {
        if let Some(lock) = &data.lock
            && lock.surface == *surface
        {
            err = true;
        }
        if let Some(lsurf) = &data.confined
            && lsurf == surface
        {
            err = true;
        }
    });
    err
}

impl Dispatch<ZwpPointerConstraintsV1, ()> for WLCState {
    fn request(
        state: &mut Self,
        _client: &Client,
        resource: &ZwpPointerConstraintsV1,
        request: zwp_constraints::Request,
        _data: &(),
        _disp: &DisplayHandle,
        data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            zwp_constraints::Request::Destroy => {}
            zwp_constraints::Request::LockPointer {
                id,
                surface,
                pointer,
                ..
            } => {
                if has_existing_constraint(state, &pointer, &surface) {
                    resource.post_error(
                        zwp_constraints::Error::AlreadyConstrained,
                        "Pointer or surface already has attached constraint",
                    );
                    return;
                }

                let lock_resource = data_init.init(id, pointer.clone());

                with_pointer_data(&pointer, |data| {
                    data.lock = Some(WLCPointerLock {
                        locked_pointer: lock_resource,
                        surface: surface.clone(),
                        active: false,
                    });
                });
            }
            zwp_constraints::Request::ConfinePointer {
                id,
                surface,
                pointer,
                ..
            } => {
                if has_existing_constraint(state, &pointer, &surface) {
                    resource.post_error(
                        zwp_constraints::Error::AlreadyConstrained,
                        "Pointer or surface already has attached constraint",
                    );
                    return;
                }

                with_pointer_data(&pointer, |data| {
                    data.confined = Some(surface.clone());
                });

                let _confine_resource = data_init.init(id, pointer.clone());
            }
            _ => unreachable!(),
        }
    }
}

impl Dispatch<ZwpLockedPointerV1, WlPointer> for WLCState {
    fn request(
        _state: &mut Self,
        _client: &Client,
        _resource: &ZwpLockedPointerV1,
        request: zwp_locked::Request,
        _data: &WlPointer,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            zwp_locked::Request::Destroy => {}
            zwp_locked::Request::SetCursorPositionHint { .. } => {}
            zwp_locked::Request::SetRegion { .. } => {}
            _ => unreachable!(),
        }
    }

    fn destroyed(
        _state: &mut Self,
        _client: ClientId,
        _locked_resource: &ZwpLockedPointerV1,
        pointer: &WlPointer,
    ) {
        with_pointer_data(pointer, |data| {
            data.lock = None;
        });
    }
}

impl Dispatch<ZwpConfinedPointerV1, WlPointer> for WLCState {
    fn request(
        _state: &mut Self,
        _client: &Client,
        _resource: &ZwpConfinedPointerV1,
        request: zwp_confined::Request,
        _data: &WlPointer,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            zwp_confined::Request::Destroy => {}
            zwp_confined::Request::SetRegion { .. } => {}
            _ => unreachable!(),
        }
    }

    fn destroyed(
        _state: &mut Self,
        _client: ClientId,
        _confined_resource: &ZwpConfinedPointerV1,
        pointer: &WlPointer,
    ) {
        with_pointer_data(pointer, |data| {
            data.confined = None;
        });
    }
}

impl GlobalDispatch<WpCursorShapeManagerV1, ()> for WLCState {
    fn bind(
        _state: &mut Self,
        _handle: &DisplayHandle,
        _client: &Client,
        resource: New<WpCursorShapeManagerV1>,
        _data: &(),
        data_init: &mut DataInit<'_, Self>,
    ) {
        data_init.init(resource, ());
    }
}

impl Dispatch<WpCursorShapeManagerV1, ()> for WLCState {
    fn request(
        _state: &mut Self,
        _client: &Client,
        _resource: &WpCursorShapeManagerV1,
        request: wp_cursor_shape_manager_v1::Request,
        _data: &(),
        _disp: &DisplayHandle,
        data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wp_cursor_shape_manager_v1::Request::Destroy => {}
            wp_cursor_shape_manager_v1::Request::GetPointer {
                cursor_shape_device,
                pointer,
            } => {
                let device_data = WLCCursorShapeDeviceData {
                    pointer: Some(pointer),
                };
                let device_data = Arc::new(Mutex::new(device_data));
                data_init.init(cursor_shape_device, device_data);
            }
            wp_cursor_shape_manager_v1::Request::GetTabletToolV2 {
                cursor_shape_device,
                ..
            } => {
                let device_data = WLCCursorShapeDeviceData { pointer: None };
                let device_data = Arc::new(Mutex::new(device_data));
                data_init.init(cursor_shape_device, device_data);
            }
            _ => unreachable!(),
        }
    }
}

impl Dispatch<WpCursorShapeDeviceV1, WLCCursorShapeDevice> for WLCState {
    fn request(
        state: &mut Self,
        _client: &Client,
        device: &WpCursorShapeDeviceV1,
        request: wp_cursor_shape_device_v1::Request,
        _data: &WLCCursorShapeDevice,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wp_cursor_shape_device_v1::Request::Destroy => {}
            wp_cursor_shape_device_v1::Request::SetShape { shape, serial } => {
                let pointer = with_cursor_shape_device_data(device, |data| {
                    data.pointer.clone()
                });

                if pointer.is_none() {
                    // No tablet support
                    return;
                }
                let pointer = pointer.unwrap();

                let last_enter =
                    with_pointer_data(&pointer, |data| data.last_enter);
                if last_enter.is_none() {
                    return;
                }
                if last_enter.unwrap() != serial {
                    return;
                }

                state.seat.cursor_shape = Some(shape.into());
            }
            _ => unreachable!(),
        }
    }
}
