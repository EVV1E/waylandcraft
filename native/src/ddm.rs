use crate::WLCState;
use crate::utils::{get_time, new_serial, to_fixed2};
use smithay::reexports::wayland_server::{
    Client, DataInit, Dispatch, DisplayHandle, GlobalDispatch, New, Resource,
    backend::ClientId,
    protocol::{
        wl_data_device::{self, WlDataDevice},
        wl_data_device_manager as wl_ddm,
        wl_data_device_manager::DndAction,
        wl_data_device_manager::WlDataDeviceManager as WlDDM,
        wl_data_offer::{self, WlDataOffer},
        wl_data_source::{self, WlDataSource},
        wl_surface::WlSurface,
    },
};
use std::ops::DerefMut;
use std::os::fd::AsFd;
use std::sync::{Arc, Mutex};

pub struct WLCDataState {
    pub devices: Vec<WlDataDevice>,
    pub clipboard: Option<WlDataSource>,
    pub clipboard_focus: Option<Client>,
    pub dnd: Option<WLCDndEvent>,
    display_handle: DisplayHandle,
}

// Drag and drop session
// `dropped` is set when the user successfully performed a drop over a surface
pub struct WLCDndEvent {
    pub start_serial: u32,
    pub request_sent: bool,
    pub client: Client,
    pub source: Option<WlDataSource>,
    pub icon: Option<WlSurface>,
    pub focus: Option<WlSurface>,
    pub mime: Option<String>,
    pub action: DndAction,
    pub dropped: bool,
}

#[derive(Debug, PartialEq)]
enum SourceUsage {
    Unused,
    Selection,
    Drag,
}

type WLCDataSource = Arc<Mutex<WLCDataSourceData>>;
struct WLCDataSourceData {
    usage: SourceUsage,
    mime: Vec<String>,
    actions: DndAction,
}

type WLCDataOffer = Arc<Mutex<WLCDataOfferData>>;
struct WLCDataOfferData {
    // NOTE: The source is in the id space of the source client!!
    source: WlDataSource,
    device: WlDataDevice,
}

type WLCDataDevice = Arc<Mutex<WLCDataDeviceData>>;
struct WLCDataDeviceData {
    // Device focus (see enter, leave)
    dnd_focus: Option<WlSurface>,
    last_dnd_motion: Option<(i32, i32)>,
    // Currently active data offer. May be present even when no dnd_focus set
    dnd_offer: Option<WlDataOffer>,
}

fn with_source_data<F, R>(source: &WlDataSource, f: F) -> R
where
    F: FnOnce(&mut WLCDataSourceData) -> R,
{
    let mut guard = source.data::<WLCDataSource>().unwrap().lock().unwrap();
    let data = guard.deref_mut();
    f(data)
}

fn with_offer_data<F, R>(offer: &WlDataOffer, f: F) -> R
where
    F: FnOnce(&mut WLCDataOfferData) -> R,
{
    let mut guard = offer.data::<WLCDataOffer>().unwrap().lock().unwrap();
    let data = guard.deref_mut();
    f(data)
}

fn with_device_data<F, R>(device: &WlDataDevice, f: F) -> R
where
    F: FnOnce(&mut WLCDataDeviceData) -> R,
{
    let mut guard = device.data::<WLCDataDevice>().unwrap().lock().unwrap();
    let data = guard.deref_mut();
    f(data)
}

impl WLCDataState {
    pub fn new(display_handle: &DisplayHandle) -> Self {
        WLCDataState {
            devices: vec![],
            clipboard: None,
            clipboard_focus: None,
            dnd: None,
            display_handle: display_handle.clone(),
        }
    }

    pub fn create_global(&self) {
        self.display_handle
            .create_global::<WLCState, WlDDM, ()>(3, ());
    }

    pub fn update_clipboard_client(&mut self, client: Option<Client>) {
        if self.clipboard_focus != client {
            self.clipboard_focus = client;
            self.send_clipboard();
        }
    }

    // Send clipboard data to client with clipboard focus
    fn send_clipboard(&self) {
        let client = match &self.clipboard_focus {
            Some(c) => c,
            None => {
                return;
            }
        };
        for device in &self.devices {
            if !device.client().is_some_and(|c| c == *client) {
                continue;
            }

            if let Some(clipboard) = &self.clipboard {
                let offer_data = WLCDataOfferData {
                    source: clipboard.clone(),
                    device: device.clone(),
                };
                let offer_data = Arc::new(Mutex::new(offer_data));
                let offer = client
                    .create_resource::<WlDataOffer, WLCDataOffer, WLCState>(
                        &self.display_handle,
                        device.version(),
                        offer_data,
                    )
                    .unwrap();

                device.data_offer(&offer);
                with_source_data(clipboard, |data| {
                    for m in data.mime.iter().cloned() {
                        offer.offer(m);
                    }
                });
                device.selection(Some(&offer));
            } else {
                device.selection(None);
            }
        }
    }

    fn print_dnd_debug(&self, header: &str) {
        /*
        println!("\n{}", header);
        print!("DND: ");
        if self.dnd.is_none() {
            println!("NONE");
            return;
        }
        println!();
        let dnd = self.dnd.as_ref().unwrap();
        println!("\tsource: {:?}", dnd.source);
        println!("\tfocus: {:?}", dnd.focus);
        println!("\tmime: {:?}", dnd.mime);
        println!("\taction: {:?}", dnd.action);
        println!("\tdropped: {:?}", dnd.dropped);
        */
        let _ = header;
    }

    pub fn check_dnd_request(&mut self) -> Option<u32> {
        let dnd = self.dnd.as_mut()?;
        if dnd.request_sent {
            return None;
        }
        dnd.request_sent = true;
        Some(dnd.start_serial)
    }

    fn dnd_send_offer(
        &self,
        source: &WlDataSource,
        device: &WlDataDevice,
        data: &mut WLCDataDeviceData,
    ) -> WlDataOffer {
        let device_client = device.client().unwrap();

        // Create offer
        let offer_data = WLCDataOfferData {
            source: source.clone(),
            device: device.clone(),
        };
        let offer_data = Arc::new(Mutex::new(offer_data));
        let offer = device_client
            .create_resource::<WlDataOffer, WLCDataOffer, WLCState>(
                &self.display_handle,
                device.version(),
                offer_data,
            )
            .unwrap();
        data.dnd_offer = Some(offer.clone());

        // Send offer to client
        device.data_offer(&offer);
        with_source_data(source, |data| {
            for m in data.mime.iter().cloned() {
                offer.offer(m);
            }
            offer.source_actions(data.actions);
        });

        offer
    }

    pub fn dnd_motion(
        &mut self,
        mut surface: Option<&WlSurface>,
        x: f64,
        y: f64,
    ) {
        self.print_dnd_debug("dnd motion");

        if self.dnd.is_none() {
            return;
        }
        if self.dnd.as_ref().unwrap().dropped {
            return;
        }

        let client = self.dnd.as_ref().unwrap().client.clone();
        let source = self.dnd.as_ref().unwrap().source.clone();
        let focus = self.dnd.as_ref().unwrap().focus.clone();

        if source.is_none()
            && let Some(s) = surface
        {
            let surface_client = s.client().unwrap();
            if surface_client != client {
                // Non-source drag and focus is on a different client
                surface = None;
            }
        }

        if surface != focus.as_ref() {
            // Reset the accepted type when moving to different surface
            if let Some(s) = &source {
                s.target(None);
            }
            self.dnd.as_mut().unwrap().mime = None;
        }
        self.dnd.as_mut().unwrap().focus = surface.cloned();

        // Unfocus devices focused on wrong surface
        self.for_all_devices(|device, data| {
            let focus = match &data.dnd_focus {
                Some(s) => s,
                None => return,
            };
            let unfocus = match surface {
                Some(s) => s != focus,
                None => true,
            };
            if unfocus {
                device.leave();
                data.dnd_focus = None;
            }
        });

        let surface = match surface {
            Some(s) => s,
            None => return,
        };

        // Send device enter events
        self.for_all_devices(|device, data| {
            // Already focused
            if data.dnd_focus.is_some() {
                return;
            }

            // Check if client does not own surface
            let surface_client = surface.client().unwrap();
            let device_client = device.client().unwrap();
            if surface_client != device_client {
                return;
            }

            let mut offer = None;
            if let Some(ref s) = source {
                offer = Some(self.dnd_send_offer(s, device, data));
            }

            // Make device enter surface
            device.enter(new_serial(), surface, x, y, offer.as_ref());
            data.dnd_focus = Some(surface.clone());
            data.last_dnd_motion = None;
        });

        let time = get_time();
        let pos: (i32, i32) = to_fixed2(x, y);

        // Send device motion events
        self.for_all_devices(|device, data| {
            if data.dnd_focus.is_none() {
                return;
            }
            if data.last_dnd_motion == Some(pos) {
                return;
            }

            device.motion(time, x, y);
            data.last_dnd_motion = Some(pos);
        });
    }

    pub fn dnd_drop(&mut self) {
        self.print_dnd_debug("dnd drop");
        let dnd = match &mut self.dnd {
            Some(d) => d,
            None => return,
        };
        if dnd.dropped {
            return;
        }
        dnd.dropped = true;

        let action = dnd.action;
        if dnd.focus.is_none() || dnd.mime.is_none() || action.is_empty() {
            self.dnd_cancel();
            return;
        }

        if let Some(s) = dnd.source.as_ref() {
            s.action(action);
            s.dnd_drop_performed();
        }

        self.for_all_devices(|device, data| {
            if data.dnd_focus.is_none() {
                return;
            }
            data.dnd_focus = None;
            data.dnd_offer.as_ref().unwrap().action(action);
            device.drop();
            device.leave();
        });

        if self.dnd.as_ref().unwrap().source.is_none() {
            // Immediately destroy dnd when the drag doesn't have a source
            self.dnd = None;
        }
    }

    fn unfocus_devices(&self) {
        self.for_all_devices(|device, data| {
            match data.dnd_focus.take() {
                Some(_) => (),
                None => return,
            };
            device.leave();
        });
    }

    pub fn dnd_cancel(&mut self) {
        self.print_dnd_debug("dnd cancel");
        let dnd = match &self.dnd {
            Some(d) => d,
            None => return,
        };
        self.unfocus_devices();

        if let Some(s) = dnd.source.as_ref() {
            s.cancelled();
        } else {
            // Immediately destroy dnd when the drag doesn't have a source
            self.dnd = None;
        }
    }

    fn for_all_devices<F>(&self, mut f: F)
    where
        F: FnMut(&WlDataDevice, &mut WLCDataDeviceData),
    {
        for device in &self.devices {
            with_device_data(device, |data| f(device, data));
        }
    }
}

impl GlobalDispatch<WlDDM, ()> for WLCState {
    fn bind(
        _state: &mut Self,
        _handle: &DisplayHandle,
        _client: &Client,
        resource: New<WlDDM>,
        _data: &(),
        data_init: &mut DataInit<'_, Self>,
    ) {
        let _ddm: WlDDM = data_init.init(resource, ());
    }
}

impl Dispatch<WlDDM, ()> for WLCState {
    fn request(
        state: &mut Self,
        _client: &Client,
        _ddm: &WlDDM,
        request: wl_ddm::Request,
        _data: &(),
        _disp: &DisplayHandle,
        data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_ddm::Request::CreateDataSource { id } => {
                let source_data = WLCDataSourceData {
                    usage: SourceUsage::Unused,
                    mime: vec![],
                    actions: DndAction::None,
                };
                let source_data = Arc::new(Mutex::new(source_data));
                let _source = data_init.init(id, source_data.clone());
            }
            wl_ddm::Request::GetDataDevice { id, .. } => {
                let device_data = WLCDataDeviceData {
                    dnd_focus: None,
                    last_dnd_motion: None,
                    dnd_offer: None,
                };
                let device_data = Arc::new(Mutex::new(device_data));
                let device = data_init.init(id, device_data.clone());

                state.data.devices.push(device);
            }
            _ => unreachable!(),
        }
    }
}

impl Dispatch<WlDataSource, WLCDataSource> for WLCState {
    fn request(
        state: &mut Self,
        _client: &Client,
        source: &WlDataSource,
        request: wl_data_source::Request,
        _data: &WLCDataSource,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_data_source::Request::Offer { mime_type } => {
                with_source_data(source, |data| {
                    data.mime.push(mime_type);
                });
            }
            wl_data_source::Request::Destroy => {
                let dnd = match &state.data.dnd {
                    Some(d) => d,
                    None => return,
                };
                if Some(source) == dnd.source.as_ref() {
                    state.data.print_dnd_debug("data source destroy");
                    state.data.unfocus_devices();
                    state.data.dnd = None;
                }
            }
            wl_data_source::Request::SetActions { dnd_actions } => {
                let actions = match dnd_actions.into_result() {
                    Ok(a) => a,
                    Err(_) => return,
                };

                with_source_data(source, |data| {
                    data.actions = actions;
                });

                state.data.for_all_devices(|_device, data| {
                    if data.dnd_offer.is_none() {
                        return;
                    }
                    let offer = data.dnd_offer.as_ref().unwrap();
                    let matches_source =
                        with_offer_data(offer, |data| data.source == *source);
                    if matches_source {
                        offer.source_actions(actions);
                    }
                });
            }
            _ => unreachable!(),
        }
    }

    fn destroyed(
        state: &mut Self,
        _client: ClientId,
        source: &WlDataSource,
        _data: &WLCDataSource,
    ) {
        if state.data.clipboard.as_ref().is_some_and(|c| c == source) {
            state.data.clipboard = None;
        }
        if let Some(dnd) = &state.data.dnd
            && Some(source) == dnd.source.as_ref()
        {
            state.data.unfocus_devices();
            state.data.dnd = None;
        }
    }
}

impl Dispatch<WlDataDevice, WLCDataDevice> for WLCState {
    fn request(
        state: &mut Self,
        client: &Client,
        device: &WlDataDevice,
        request: wl_data_device::Request,
        _data: &WLCDataDevice,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_data_device::Request::StartDrag {
                source,
                serial,
                icon,
                ..
            } => {
                if let Some(ref s) = source {
                    with_source_data(s, |data| {
                        if data.usage != SourceUsage::Unused {
                            device.post_error(
                                wl_data_device::Error::UsedSource,
                                "reused data source",
                            );
                            return;
                        }
                        data.usage = SourceUsage::Drag;
                    });
                }

                state.data.print_dnd_debug("drag start");

                // Cancel if drag is already active
                if state.data.dnd.is_some() {
                    if let Some(s) = source {
                        s.cancelled();
                    }
                    return;
                }

                state.data.dnd = Some(WLCDndEvent {
                    start_serial: serial,
                    request_sent: false,
                    client: client.clone(),
                    source: source.clone(),
                    icon: icon.clone(),
                    focus: None,
                    mime: None,
                    action: DndAction::None,
                    dropped: false,
                });
            }
            wl_data_device::Request::SetSelection { source, serial: _ } => {
                let focus = state.data.clipboard_focus.as_ref();
                if !focus.is_some_and(|c| c == client) {
                    return;
                }

                if let Some(source) = &source {
                    let mime =
                        with_source_data(source, |data| data.mime.clone());

                    // STOP SENDING ME EMPTY CLIPBOARD SELECTIONS WITH THE
                    // SAVE_TARGETS MIME. I HAVE NO CLUE WHAT THAT IS.
                    // WHYYYYYYYY. I blame X11.
                    if mime.iter().any(|m| m == "SAVE_TARGETS") {
                        return;
                    }

                    with_source_data(source, |data| {
                        if data.usage != SourceUsage::Unused {
                            device.post_error(
                                wl_data_device::Error::UsedSource,
                                "reused data source",
                            );
                            return;
                        }
                        data.usage = SourceUsage::Selection;
                    });
                }

                if let Some(old_clipboard) = &state.data.clipboard {
                    old_clipboard.cancelled();
                }
                state.data.clipboard = source;
                state.data.send_clipboard();
            }
            wl_data_device::Request::Release => {}
            _ => unreachable!(),
        }
    }

    fn destroyed(
        state: &mut Self,
        _client: ClientId,
        device: &WlDataDevice,
        _data: &WLCDataDevice,
    ) {
        state.data.devices.retain(|d| d != device);
    }
}

impl Dispatch<WlDataOffer, WLCDataOffer> for WLCState {
    fn request(
        state: &mut Self,
        _client: &Client,
        offer: &WlDataOffer,
        request: wl_data_offer::Request,
        _data: &WLCDataOffer,
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_data_offer::Request::Receive { mime_type, fd } => {
                with_offer_data(offer, |data| {
                    if !data.source.is_alive() {
                        return;
                    }

                    let mime = with_source_data(&data.source, |source_data| {
                        source_data.mime.clone()
                    });

                    if !mime.contains(&mime_type) {
                        return;
                    }

                    data.source.send(mime_type, fd.as_fd());
                });
            }
            wl_data_offer::Request::Accept { mime_type, .. } => {
                let dnd = match &mut state.data.dnd {
                    Some(d) => d,
                    None => return,
                };
                with_offer_data(offer, |data| {
                    if Some(&data.source) != dnd.source.as_ref() {
                        return;
                    }
                    data.source.target(mime_type.clone());
                    dnd.mime = mime_type;
                });
            }
            wl_data_offer::Request::Destroy => {
                with_offer_data(offer, |data| {
                    with_device_data(&data.device, |dev| {
                        if dev.dnd_offer.as_ref() == Some(offer) {
                            dev.dnd_offer = None;
                        }
                    });
                });
            }
            wl_data_offer::Request::Finish => {
                with_offer_data(offer, |data| {
                    data.source.dnd_finished();
                });
            }
            wl_data_offer::Request::SetActions {
                dnd_actions,
                preferred_action,
            } => {
                let dnd_actions = match dnd_actions.into_result() {
                    Ok(a) => a,
                    Err(_) => return,
                };
                let preferred_action = match preferred_action.into_result() {
                    Ok(a) => a,
                    Err(_) => return,
                };

                let source_actions = with_offer_data(offer, |data| {
                    with_source_data(&data.source, |sdata| sdata.actions)
                });

                let actions = dnd_actions & source_actions;
                let action = if actions.contains(preferred_action)
                    && preferred_action != DndAction::Ask
                {
                    preferred_action
                } else {
                    actions
                        .iter()
                        .find(|a| *a != DndAction::Ask)
                        .unwrap_or(DndAction::None)
                };

                assert!(action.iter().count() <= 1);
                assert_eq!(action.iter().find(|a| *a == DndAction::Ask), None);
                assert!(actions.contains(action));

                let dnd = match state.data.dnd.as_mut() {
                    Some(d) => d,
                    None => return,
                };
                dnd.action = action;

                if dnd.source.is_none() {
                    return;
                }

                offer.action(dnd.action);
                dnd.source.as_ref().unwrap().action(dnd.action);
            }
            _ => unreachable!(),
        }
    }

    fn destroyed(
        _state: &mut Self,
        _client: ClientId,
        _offer: &WlDataOffer,
        _data: &WLCDataOffer,
    ) {
    }
}
