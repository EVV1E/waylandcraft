package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.grabs.PointerGrabMap.ImplicitGrab;
import net.minecraft.world.phys.Vec3;

public class DNDGrab extends PointerGrab {
	
	public DNDGrab(ImplicitGrab implicit) {
		super(implicit.button());
	}
	
	@Override
	public void init() throws GrabDroppedException {
		wlc.bridge.sendMotionOutside();
	}
	
	@Override
	public void release(boolean force) throws GrabDroppedException {
		if(!force) {
			wlc.bridge.dndDrop();
		} else {
			wlc.bridge.dndCancel();
		}
	}
	
	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up, float yRot, float xRot) throws GrabDroppedException {
	}
	
	@Override
	public void hover(WLCAbstractWindow window, WLCSurface surface, double x, double y) throws GrabDroppedException {
		wlc.bridge.sendDndMotion(surface, x, y);
	}
	
	@Override
	public void hoverNone() throws GrabDroppedException {
		wlc.bridge.sendDndMotion(null, 0, 0);
	}
	
}
