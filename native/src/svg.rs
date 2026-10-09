use resvg::{
    tiny_skia::{Color, PixmapMut, Transform},
    usvg::Tree,
};
use std::fs::File;
use std::io::Read;
use std::path::PathBuf;

// Render svg file found at path to byte buffer data with resolution
// width x height. data has to be a buffer of at least 4 * width * height.
pub fn render_svg(
    path: PathBuf,
    width: u32,
    height: u32,
    data: *mut u8,
) -> Option<()> {
    let data_len = (width * height * 4) as usize;
    let data = unsafe { std::slice::from_raw_parts_mut(data, data_len) };
    let mut pixmap = PixmapMut::from_bytes(data, width, height)?;
    pixmap.fill(Color::TRANSPARENT);

    let mut file = File::open(&path).ok()?;
    let mut svg_data = String::new();
    file.read_to_string(&mut svg_data).ok()?;
    drop(file);

    let tree = Tree::from_str(&svg_data, &Default::default()).ok()?;
    let transform = Transform::from_scale(
        width as f32 / tree.size().width(),
        height as f32 / tree.size().height(),
    );

    resvg::render(&tree, transform, &mut pixmap);

    Some(())
}
