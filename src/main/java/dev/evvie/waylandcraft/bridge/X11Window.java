package dev.evvie.waylandcraft.bridge;

import org.jetbrains.annotations.Nullable;

public class X11Window extends WLCAbstractWindow 
{
	public final long x11Handle;
	public @Nullable String title;
	public @Nullable String appID;

	public X11Window(long handle) 
    {
		super(handle);
		this.x11Handle = handle;
		WLCSurface root = new WLCSurface(handle);
		this.surface = root;
		this.lastChild = root;
		this.geometry = new SurfaceGeometry(0, 0, 1, 1);
	}

	public void updateGeometry(int x, int y, int width, int height) 
    {
		this.geometry = new SurfaceGeometry(x, y, width, height);
	}
}
