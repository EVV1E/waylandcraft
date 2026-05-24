use crate::xdg_spec::XDGSpecHelper;
use std::collections::HashMap;
use std::error::Error;
use x11rb::{
    connection::Connection,
    protocol::{
        Event,
        xproto::{
            Atom, AtomEnum, ChangeWindowAttributesAux, ConnectionExt,
            EventMask, GetGeometryReply, ImageFormat, MapState, Window,
        },
    },
    rust_connection::RustConnection,
};

const MAX_CAPTURE_WIDTH: u16 = 8192;
const MAX_CAPTURE_HEIGHT: u16 = 8192;
const MAX_CAPTURE_BYTES: usize = 256 * 1024 * 1024;

#[derive(Clone)]
pub struct X11Window {
    pub id: Window,
    pub mapped: bool,
    pub x: i16,
    pub y: i16,
    pub width: u16,
    pub height: u16,
    pub title: Option<String>,
    pub app_id: Option<String>,
    pub pixels: Vec<u8>,
}

pub struct X11Craft {
    conn: RustConnection,
    root: Window,
    pub display: String,
    windows: HashMap<Window, X11Window>,
    atom_net_wm_name: Atom,
    atom_utf8_string: Atom,
    atom_wm_class: Atom,
    pub xdg: XDGSpecHelper,
}

impl X11Craft {
    pub fn init(display: String) -> Result<Self, Box<dyn Error>> {
        let (conn, screen_num) =
            RustConnection::connect(Some(display.as_str()))?;
        let root = conn.setup().roots[screen_num].root;

        let atom_net_wm_name =
            conn.intern_atom(false, b"_NET_WM_NAME")?.reply()?.atom;
        let atom_utf8_string =
            conn.intern_atom(false, b"UTF8_STRING")?.reply()?.atom;
        let atom_wm_class = conn.intern_atom(false, b"WM_CLASS")?.reply()?.atom;

        let attrs = ChangeWindowAttributesAux::new().event_mask(
            EventMask::SUBSTRUCTURE_NOTIFY | EventMask::PROPERTY_CHANGE,
        );
        let _ = conn.change_window_attributes(root, &attrs);

        let instance = Self {
            conn,
            root,
            display,
            windows: HashMap::new(),
            atom_net_wm_name,
            atom_utf8_string,
            atom_wm_class,
            xdg: XDGSpecHelper::init(),
        };

        Ok(instance)
    }

    pub fn update(&mut self) {
        while let Ok(Some(event)) = self.conn.poll_for_event() {
            match event {
                Event::MapNotify(ev) => {
                    self.track_window(ev.window, true);
                }
                Event::UnmapNotify(ev) => {
                    if let Some(window) = self.windows.get_mut(&ev.window) {
                        window.mapped = false;
                    }
                }
                Event::DestroyNotify(ev) => {
                    self.windows.remove(&ev.window);
                }
                Event::ConfigureNotify(ev) => {
                    self.on_configure(ev.window, ev.x, ev.y, ev.width, ev.height);
                }
                Event::PropertyNotify(ev) => {
                    self.refresh_window_properties(ev.window);
                }
                _ => {}
            }
        }

        let _ = self.conn.flush();
    }

    pub fn mapped_windows(&self) -> Vec<Window> {
        self.windows
            .values()
            .filter(|w| w.mapped)
            .map(|w| w.id)
            .collect()
    }

    pub fn get_window(&self, id: Window) -> Option<&X11Window> {
        self.windows.get(&id)
    }

    pub fn capture_window(
        &mut self,
        id: Window,
    ) -> Option<(*const u8, usize, u16, u16, i32)> {
        let (w, h, mapped) = {
            let meta = self.windows.get(&id)?;
            (meta.width, meta.height, meta.mapped)
        };

        if !mapped || w == 0 || h == 0 {
            return None;
        }
        if !Self::capture_size_allowed(w, h) {
            return None;
        }

        let reply = self
            .conn
            .get_image(ImageFormat::Z_PIXMAP, id, 0, 0, w, h, !0u32)
            .ok()?
            .reply()
            .ok()?;

        let expected = (w as usize) * (h as usize) * 4;
        if expected > MAX_CAPTURE_BYTES || reply.data.len() < expected {
            return None;
        }

        let meta = self.windows.get_mut(&id)?;
        meta.pixels.clear();
        meta.pixels.extend_from_slice(&reply.data[..expected]);

        Some((meta.pixels.as_ptr(), expected, w, h, (w as i32) * 4))
    }

    fn track_window(&mut self, window: Window, force_mapped: bool) {
        if window == self.root {
            return;
        }

        let attrs = match self
            .conn
            .get_window_attributes(window)
            .ok()
            .and_then(|cookie| cookie.reply().ok())
        {
            Some(attrs) => attrs,
            None => return,
        };

        let mapped = force_mapped || attrs.map_state == MapState::VIEWABLE;
        let geometry = match self.window_geometry(window) {
            Some(geometry) => geometry,
            None => return,
        };
        if !Self::capture_size_allowed(geometry.width, geometry.height) {
            return;
        }

        let _ = self.conn.change_window_attributes(
            window,
            &ChangeWindowAttributesAux::new().event_mask(EventMask::PROPERTY_CHANGE),
        );

        let title = self.read_window_title(window);
        let app_id = self.read_window_app_id(window);

        self.windows.insert(
            window,
            X11Window {
                id: window,
                mapped,
                x: geometry.x,
                y: geometry.y,
                width: geometry.width,
                height: geometry.height,
                title,
                app_id,
                pixels: vec![],
            },
        );
    }

    fn window_geometry(&self, window: Window) -> Option<GetGeometryReply> {
        self.conn
            .get_geometry(window)
            .ok()
            .and_then(|cookie| cookie.reply().ok())
    }

    fn on_configure(&mut self, window: Window, x: i16, y: i16, w: u16, h: u16) {
        if !Self::capture_size_allowed(w, h) {
            self.windows.remove(&window);
            return;
        }

        if let Some(meta) = self.windows.get_mut(&window) {
            meta.x = x;
            meta.y = y;
            meta.width = w;
            meta.height = h;
        }
    }

    fn capture_size_allowed(width: u16, height: u16) -> bool {
        if width == 0 || height == 0 {
            return false;
        }
        if width > MAX_CAPTURE_WIDTH || height > MAX_CAPTURE_HEIGHT {
            return false;
        }

        let pixels = (width as usize).saturating_mul(height as usize);
        pixels <= (MAX_CAPTURE_BYTES / 4)
    }

    fn refresh_window_properties(&mut self, window: Window) {
        if !self.windows.contains_key(&window) {
            return;
        }
        let title = self.read_window_title(window);
        let app_id = self.read_window_app_id(window);
        if let Some(meta) = self.windows.get_mut(&window) {
            meta.title = title;
            meta.app_id = app_id;
        }
    }

    fn read_window_title(&self, window: Window) -> Option<String> {
        let utf8_reply = self
            .conn
            .get_property(
                false,
                window,
                self.atom_net_wm_name,
                self.atom_utf8_string,
                0,
                1024,
            )
            .ok()
            .and_then(|cookie| cookie.reply().ok());

        if let Some(reply) = utf8_reply {
            if reply.value_len > 0 {
                if let Ok(title) = String::from_utf8(reply.value) {
                    let title = title.trim_matches('\0').trim().to_string();
                    if !title.is_empty() {
                        return Some(title);
                    }
                }
            }
        }

        let reply = self
            .conn
            .get_property(
                false,
                window,
                AtomEnum::WM_NAME,
                AtomEnum::STRING,
                0,
                1024,
            )
            .ok()
            .and_then(|cookie| cookie.reply().ok())?;

        let title = String::from_utf8(reply.value).ok()?;
        let title = title.trim_matches('\0').trim().to_string();
        if title.is_empty() {
            None
        } else {
            Some(title)
        }
    }

    fn read_window_app_id(&self, window: Window) -> Option<String> {
        let reply = self
            .conn
            .get_property(
                false,
                window,
                self.atom_wm_class,
                AtomEnum::STRING,
                0,
                256,
            )
            .ok()
            .and_then(|cookie| cookie.reply().ok())?;

        let data = String::from_utf8(reply.value).ok()?;
        for token in data.split('\0') {
            let token = token.trim();
            if !token.is_empty() {
                return Some(token.to_string());
            }
        }
        None
    }
}
