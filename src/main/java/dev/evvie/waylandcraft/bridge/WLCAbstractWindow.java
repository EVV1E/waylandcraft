package dev.evvie.waylandcraft.bridge;

import dev.evvie.waylandcraft.render.WindowFramebuffer;

public abstract class WLCAbstractWindow {
	
	private long handle;
	
	protected WLCSurface surface;
	
	protected boolean wasMapped = false;
	
	public SurfaceGeometry geometry;
	
	public WLCAbstractWindow(long handle, WLCSurface surface) {
		this.handle = handle;
		this.surface = surface;
		this.geometry = new SurfaceGeometry(0, 0, 0, 0);
	}
	
	public boolean isAlive() {
		return getHandle() != 0;
	}
	
	public WLCSurface getRootSurface() {
		return this.surface;
	}
	
	public WindowFramebuffer getFramebuffer() {
		return surface.getFramebuffer();
	}
	
	public boolean isMapped() {
		return isAlive() && getRootSurface().getBuffer() != null;
	}
	
	public long getHandle() {
		return handle;
	}
	
	protected void defaultGeometry() {
		this.geometry = new SurfaceGeometry(0, 0, surface.width(), surface.height());
	}
	
	protected void updateGeometry(int x, int y, int width, int height) {
		this.geometry = new SurfaceGeometry(x, y, width, height);
	}
	
	public static record SurfaceGeometry(int x, int y, int width, int height) {}
	
}
