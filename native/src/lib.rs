use crate::bridge::BridgeState;
use crate::ddm::WLCDataState;
use crate::desktop::DesktopHelper;
use crate::output::WLCOutput;
use crate::satellite::SatelliteState;
use crate::seat::WLCSeatState;
use libc::dev_t;
use smithay::{
    backend::allocator::{Format, dmabuf::Dmabuf},
    delegate_compositor, delegate_dmabuf, delegate_shm,
    delegate_single_pixel_buffer, delegate_viewporter, delegate_xdg_shell,
    reexports::{
        calloop::{self, EventLoop, generic::Generic as GenericEvent},
        wayland_protocols::xdg::shell::server::xdg_toplevel::{
            ResizeEdge, WmCapabilities,
        },
        wayland_server::{
            self, Display, DisplayHandle,
            backend::{ClientData, ClientId, DisconnectReason},
            protocol::{
                wl_buffer::WlBuffer, wl_output::WlOutput, wl_seat::WlSeat,
                wl_surface::WlSurface,
            },
        },
    },
    utils::Serial,
    wayland::{
        buffer::BufferHandler,
        compositor::{
            CompositorClientState, CompositorHandler, CompositorState,
        },
        dmabuf::{
            self, get_dmabuf, DmabufFeedbackBuilder, DmabufGlobal,
            DmabufHandler, DmabufState,
        },
        shell::xdg::{
            PopupSurface, PositionerState, ToplevelSurface, XdgShellHandler,
            XdgShellState,
        },
        shm::{ShmHandler, ShmState},
        single_pixel_buffer::SinglePixelBufferState,
        socket::ListeningSocketSource,
        viewporter::ViewporterState,
    },
};
use std::ffi::OsString;
use std::mem::MaybeUninit;
use std::sync::Arc;

mod bridge;
mod ddm;
mod desktop;
mod output;
mod process;
mod satellite;
mod seat;
mod svg;
mod utils;

// Global compositor state
pub struct WaylandCraft<'a> {
    pub state: WLCState,
    pub event_loop: EventLoop<'a, WLCState>,
    pub desktop_helper: DesktopHelper,
}

pub struct WLCState {
    pub display_handle: DisplayHandle,
    pub socket: OsString,
    pub compositor_state: CompositorState,
    pub shm_state: ShmState,
    pub xdg_state: XdgShellState,
    pub viewporter_state: ViewporterState,
    pub single_pixel_buffer_state: SinglePixelBufferState,
    pub dmabuf_state: DmabufState,
    pub dmabuf_global: MaybeUninit<DmabufGlobal>,
    pub seat: WLCSeatState,
    pub data: WLCDataState,
    pub output: WLCOutput,
    pub satellite: Option<SatelliteState>,
    pub bridge: BridgeState,
}

pub struct DmabufFeedbackData {
    device: dev_t,
    formats: Vec<Format>,
}

impl WLCState {
    fn new(
        disp: DisplayHandle,
        bridge_state: BridgeState,
        dmabuf_feedback: Option<DmabufFeedbackData>,
    ) -> Self {
        let compositor_state = CompositorState::new::<WLCState>(&disp);
        let shm_state = ShmState::new::<WLCState>(&disp, vec![]);
        let xdg_state = XdgShellState::new_with_capabilities::<WLCState>(
            &disp,
            vec![
                WmCapabilities::Maximize,
                WmCapabilities::Fullscreen,
                WmCapabilities::Minimize,
            ],
        );
        let viewporter_state = ViewporterState::new::<WLCState>(&disp);
        let single_pixel_buffer_state =
            SinglePixelBufferState::new::<WLCState>(&disp);

        let mut dmabuf_state = DmabufState::new();
        let mut dmabuf_global = MaybeUninit::uninit();

        if let Some(feedback) = dmabuf_feedback {
            let feedback = DmabufFeedbackBuilder::new(
                feedback.device,
                feedback.formats,
            )
                .build()
                .unwrap();

            let global = dmabuf_state
                .create_global_with_default_feedback::<WLCState>(
                    &disp,
                    &feedback,
                );
            dmabuf_global.write(global);
        }

        let seat = WLCSeatState::new();
        seat.create_globals(&disp);

        let data = WLCDataState::new(&disp);
        data.create_global();

        let output = WLCOutput::new(&disp);
        output.create_global();

        Self {
            display_handle: disp.clone(),
            socket: OsString::new(),
            compositor_state,
            shm_state,
            xdg_state,
            viewporter_state,
            single_pixel_buffer_state,
            dmabuf_state,
            dmabuf_global,
            seat,
            data,
            output,
            satellite: None,
            bridge: bridge_state,
        }
    }
}

impl CompositorHandler for WLCState {
    fn compositor_state(&mut self) -> &mut CompositorState {
        &mut self.compositor_state
    }

    fn client_compositor_state<'a>(
        &self,
        client: &'a wayland_server::Client,
    ) -> &'a CompositorClientState {
        &client.get_data::<WLCClient>().unwrap().compositor_state
    }

    fn new_surface(&mut self, surface: &WlSurface) {
        bridge::compositor::new_surface(self, surface);
    }

    fn destroyed(&mut self, surface: &WlSurface) {
        bridge::compositor::surface_destroyed(self, surface);
    }

    fn new_subsurface(&mut self, surface: &WlSurface, parent: &WlSurface) {
        bridge::compositor::subsurface_created(self, surface, parent);
    }

    fn commit(&mut self, surface: &WlSurface) {
        bridge::compositor::surface_commit(self, surface);
    }
}

impl BufferHandler for WLCState {
    fn buffer_destroyed(&mut self, buffer: &WlBuffer) {
        if let Ok(dmabuf) = get_dmabuf(buffer) {
            bridge::dmabuf::free_dmabuf(self, dmabuf);
        }
    }
}

impl ShmHandler for WLCState {
    fn shm_state(&self) -> &ShmState {
        &self.shm_state
    }
}

impl DmabufHandler for WLCState {
    fn dmabuf_state(&mut self) -> &mut DmabufState {
        &mut self.dmabuf_state
    }

    fn dmabuf_imported(
        &mut self,
        _global: &DmabufGlobal,
        dmabuf: Dmabuf,
        notifier: dmabuf::ImportNotifier,
    ) {
        let imported = match bridge::dmabuf::import_dmabuf(&dmabuf) {
            Ok(bridge_dmabuf) => bridge_dmabuf,
            Err(_) => {
                notifier.failed();
                return;
            },
        };

        match notifier.successful::<WLCState>() {
            Ok(_buffer) => (),
            Err(_) => {
                return;
            },
        }

        self.bridge.dmabufs.push(imported);
    }
}

impl XdgShellHandler for WLCState {
    fn xdg_shell_state(&mut self) -> &mut XdgShellState {
        &mut self.xdg_state
    }

    fn new_toplevel(&mut self, surface: ToplevelSurface) {
        surface.send_configure();
        bridge::shell::new_toplevel(self, &surface);
    }

    fn toplevel_destroyed(&mut self, surface: ToplevelSurface) {
        bridge::shell::toplevel_destroyed(self, &surface);
    }

    fn new_popup(
        &mut self,
        surface: PopupSurface,
        positioner: PositionerState,
    ) {
        surface.with_pending_state(|state| {
            state.geometry = positioner.get_geometry();
            state.positioner = positioner;
        });
        surface.send_configure().expect("popup initial configure");

        bridge::shell::new_popup(self, &surface);
    }

    fn popup_destroyed(&mut self, surface: PopupSurface) {
        bridge::shell::popup_destroyed(self, &surface);
    }

    fn grab(&mut self, _surface: PopupSurface, _seat: WlSeat, _serial: Serial) {
    }

    fn reposition_request(
        &mut self,
        surface: PopupSurface,
        positioner: PositionerState,
        token: u32,
    ) {
        surface.with_pending_state(|state| {
            state.geometry = positioner.get_geometry();
            state.positioner = positioner;
        });
        surface.send_repositioned(token);
    }

    fn maximize_request(&mut self, surface: ToplevelSurface) {
        bridge::shell::on_toplevel_maximize(self, &surface);
    }

    fn unmaximize_request(&mut self, surface: ToplevelSurface) {
        bridge::shell::on_toplevel_unmaximize(self, &surface);
    }

    fn fullscreen_request(
        &mut self,
        surface: ToplevelSurface,
        _output: Option<WlOutput>,
    ) {
        bridge::shell::on_toplevel_fullscreen(self, &surface);
    }

    fn unfullscreen_request(&mut self, surface: ToplevelSurface) {
        bridge::shell::on_toplevel_unfullscreen(self, &surface);
    }

    fn minimize_request(&mut self, surface: ToplevelSurface) {
        bridge::shell::on_toplevel_minimize(self, &surface);
    }

    fn move_request(
        &mut self,
        surface: ToplevelSurface,
        _seat: WlSeat,
        serial: Serial,
    ) {
        bridge::shell::on_toplevel_move(self, &surface, serial.into());
    }

    fn resize_request(
        &mut self,
        surface: ToplevelSurface,
        _seat: WlSeat,
        serial: Serial,
        edges: ResizeEdge,
    ) {
        bridge::shell::on_toplevel_resize(
            self,
            &surface,
            serial.into(),
            edges.into()
        );
    }

    fn app_id_changed(&mut self, surface: ToplevelSurface) {
        bridge::shell::toplevel_update_app_id(&surface);
    }

    fn title_changed(&mut self, surface: ToplevelSurface) {
        bridge::shell::toplevel_update_title(&surface);
    }
}

pub(crate) struct WLCClient {
    compositor_state: CompositorClientState,
}

impl WLCClient {
    fn new() -> Self {
        Self {
            compositor_state: CompositorClientState::default(),
        }
    }
}

impl ClientData for WLCClient {
    fn initialized(&self, _id: ClientId) {}

    fn disconnected(&self, _id: ClientId, _reason: DisconnectReason) {}
}

pub fn wlc_init(
    bridge_state: BridgeState,
    dmabuf_feedback: Option<DmabufFeedbackData>,
) -> Result<WaylandCraft<'static>, Box<dyn std::error::Error>> {
    let event_loop: EventLoop<WLCState> = EventLoop::try_new()?;
    let display: Display<WLCState> = Display::new()?;
    let socket = ListeningSocketSource::new_auto()?;

    let mut state = WLCState::new(
        display.handle(),
        bridge_state,
        dmabuf_feedback
    );
    state.socket = socket.socket_name().to_os_string();

    let ev_handle = event_loop.handle();

    ev_handle
        .insert_source(socket, |stream, _, state| {
            let client = WLCClient::new();
            state
                .display_handle
                .insert_client(stream, Arc::new(client))
                .unwrap();
        })
        .unwrap();

    let display_source = GenericEvent::new(
        display,
        calloop::Interest::READ,
        calloop::Mode::Level,
    );
    ev_handle
        .insert_source(display_source, |_, display_io, state| {
            unsafe {
                display_io.get_mut().dispatch_clients(state).unwrap();
            }
            Ok(calloop::PostAction::Continue)
        })
        .unwrap();

    let desktop_helper = DesktopHelper::init();

    match satellite::start_satellite(&state.socket) {
        Ok(s) => state.satellite = Some(s),
        Err(e) => eprintln!("Failed to start xwayland-satellite! Error: {e}"),
    }

    let instance = WaylandCraft {
        state,
        event_loop,
        desktop_helper,
    };
    Ok(instance)
}

delegate_compositor!(WLCState);
delegate_shm!(WLCState);
delegate_xdg_shell!(WLCState);
delegate_viewporter!(WLCState);
delegate_single_pixel_buffer!(WLCState);
delegate_dmabuf!(WLCState);
