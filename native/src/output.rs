use crate::WLCState;
use smithay::{
    output::{self, Output, PhysicalProperties},
    utils::{Logical, Size},
    wayland::output::OutputHandler,
};

pub struct WLCOutput {
    pub inner: Output,
    // size of content area
    bounds: Size<i32, Logical>,
}

impl WLCOutput {
    pub fn new() -> Self {
        let bounds = Size::new(1920, 1080);
        let inner = Output::new(
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
            },
        );
        let output = WLCOutput { inner, bounds };
        output.resize(bounds.w, bounds.h);
        output
    }

    pub fn size(&self) -> Size<i32, Logical> {
        self.inner.preferred_mode().unwrap().size.to_logical(1)
    }

    pub fn bounds(&self) -> Size<i32, Logical> {
        self.bounds
    }

    pub fn resize(&self, width: i32, height: i32) {
        let old_preferred = self.inner.preferred_mode();
        self.inner.set_preferred(output::Mode {
            size: Size::new(width, height),
            refresh: 0,
        });
        if let Some(old) = old_preferred {
            self.inner.delete_mode(old);
        }
    }

    pub fn set_bounds(&mut self, width: i32, height: i32) {
        self.bounds = Size::new(width, height);
    }
}

impl OutputHandler for WLCState {}
