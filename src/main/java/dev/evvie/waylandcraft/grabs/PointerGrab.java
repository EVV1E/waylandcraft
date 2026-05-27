package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import net.minecraft.world.phys.Vec3;

public abstract class PointerGrab {
	
	public final WaylandCraft wlc;
	public final int button;
	
	public PointerGrab(int button) {
		this.button = button;
		this.wlc = WaylandCraft.instance;
	}
	
	public void drop() throws GrabDroppedException {
		throw new GrabDroppedException();
	}
	
	// Called when grab is first started
	public abstract void init() throws GrabDroppedException;
	
	// Called when the pointer grab should be released
	// When `force` is set, the pointer grab was not released due to the user normally releasing their mouse button.
	// This force mode may be treated differently by some types of pointer grabs.
	public abstract void release(boolean force) throws GrabDroppedException;
	
	// Called every time the pointer is moved in the world. Arguments are world position, view vector and view up vector
	public abstract void moveWorld(Vec3 pos, Vec3 view, Vec3 up, float yRot, float xRot) throws GrabDroppedException;
	
	// Called every time the pointer is moved over a window, coordinates relative to given (sub-)surface
	public void hover(WLCAbstractWindow window, WLCSurface surface, double x, double y) throws GrabDroppedException {}
	
	// Called every time the pointer is moved in the world but does not hover any surfaces
	public void hoverNone() throws GrabDroppedException {}

	// Called every time the player scrolls while grabbing a window
	public void onScroll(double scrollX, double scrollY) throws GrabDroppedException {}
	
}
