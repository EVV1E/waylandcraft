use crate::WLCState;
use smithay::{
    output::{self, Output, PhysicalProperties},
    reexports::wayland_server::{
        Client, DataInit, Dispatch, DisplayHandle, GlobalDispatch, New,
        Resource,
        protocol::wl_output::{self, WlOutput},
    },
    utils::{Logical, Size},
};

pub struct WLCOutput {
    pub output: Output,
    // size of content area
    bounds: Size<i32, Logical>,
    display_handle: DisplayHandle,
}

impl WLCOutput {
    pub fn new(display_handle: &DisplayHandle) -> Self {
        let output = Output::new(
            "output-0".into(),
            PhysicalProperties {
                // "physical" size, not virtual
                // I'll put (1920/500*10000, 1080/500*10000)mm here
                // (assuming 1 block is 1 meter)
                size: Size::new(38400, 21600),
                subpixel: output::Subpixel::None,
                make: "Virtual".into(),
                model: "Monitor".into(),
                serial_number: "V1RT".into(),
            }
        );
        output.set_preferred(output::Mode {
            size: Size::new(1920, 1080),
            refresh: 60000,
        });
        WLCOutput {
            output,
            bounds: Size::new(1920, 1080),
            display_handle: display_handle.clone(),
        }
    }

    pub fn create_global(&self) {
        self.display_handle
            .create_global::<WLCState, WlOutput, ()>(4, ());
    }

    pub fn size(&self) -> Size<i32, Logical> {
        self.output.preferred_mode().unwrap().size.to_logical(1)
    }

    pub fn bounds(&self) -> Size<i32, Logical> {
        self.bounds
    }

    pub fn resize(&mut self, width: i32, height: i32) {
        let old_preferred = self.output.preferred_mode().unwrap();
        self.output.set_preferred(output::Mode {
            size: Size::new(width, height),
            refresh: 60000,
        });
        self.output.delete_mode(old_preferred);
    }

    pub fn set_bounds(&mut self, width: i32, height: i32) {
        self.bounds = Size::new(width, height);
    }
}

impl GlobalDispatch<WlOutput, ()> for WLCState {
    fn bind(
        state: &mut Self,
        _handle: &DisplayHandle,
        _client: &Client,
        resource: New<WlOutput>,
        _data: &(),
        data_init: &mut DataInit<'_, Self>,
    ) {
        let output: WlOutput = data_init.init(resource, ());
        let state_output = &state.output.output;

        let flags = wl_output::Mode::Current;
        if let Some(mode) = state_output.current_mode() {
            output.mode(flags, mode.size.w, mode.size.h, mode.refresh);
        }

        let location = state_output.current_location();
        let physical = state_output.physical_properties();
        output.geometry(
            location.x,
            location.y,
            physical.size.w,
            physical.size.h,
            wl_output::Subpixel::None,
            //physical.subpixel,
            physical.make,
            physical.model,
            wl_output::Transform::Normal
            //state_output.current_transform()
        );

        if output.version() >= 4 {
            output.name(state_output.name());
            output.description(state_output.description());
        }

        if output.version() >= 2 {
            output.scale(1);
            output.done();
        }
    }
}

impl Dispatch<WlOutput, ()> for WLCState {
    fn request(
        _state: &mut Self,
        _client: &Client,
        _output: &WlOutput,
        request: wl_output::Request,
        _data: &(),
        _disp: &DisplayHandle,
        _data_init: &mut DataInit<'_, Self>,
    ) {
        match request {
            wl_output::Request::Release => {}
            _ => unreachable!(),
        }
    }
}
