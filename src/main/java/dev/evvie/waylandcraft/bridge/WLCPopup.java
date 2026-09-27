package dev.evvie.waylandcraft.bridge;

import org.jetbrains.annotations.Nullable;

public class WLCPopup extends WLCAbstractWindow {
	
	@Nullable
	protected WLCAbstractWindow parent = null;
	
	public int offsetX = 0;
	public int offsetY = 0;
	
	public WLCPopup(long handle, WLCSurface surface) {
		super(handle, surface);
	}
	
	public WLCAbstractWindow getParent() {
		return parent;
	}
	
}
