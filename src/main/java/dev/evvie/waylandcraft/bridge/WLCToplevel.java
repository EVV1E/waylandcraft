package dev.evvie.waylandcraft.bridge;

import org.jetbrains.annotations.Nullable;

public class WLCToplevel extends WLCAbstractWindow {
	
	@Nullable
	public String title;
	
	@Nullable
	public String appID;
	
	public boolean fullscreen = false;
	
	@Nullable
	public SurfaceGeometry restoreGeometry = null;
	
	public WLCToplevel(long handle, WLCSurface surface) {
		super(handle, surface);
	}
	
}
