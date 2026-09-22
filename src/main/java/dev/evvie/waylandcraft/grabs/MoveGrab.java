package dev.evvie.waylandcraft.grabs;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import dev.evvie.waylandcraft.displays.WindowDisplay;
import dev.evvie.waylandcraft.grabs.PointerGrabMap.ImplicitGrab;
import dev.evvie.waylandcraft.math.WorldPlane;
import dev.evvie.waylandcraft.math.WorldPlane.Intersection;
import dev.evvie.waylandcraft.utils.CursorShape;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class MoveGrab extends PointerGrab {
	
	public final WindowDisplay window;
	public final WorldPlane plane;
	public final Vec3 initialLocal;
	public final Vec3 initialPivot;
	public final Vec3 initialWorld;
	public boolean snapActive = false;
	
	public MoveGrab(ImplicitGrab implicit) {
		super(implicit.button());
		this.window = implicit.window();
		this.initialLocal = implicit.startGeometryLocal();
		this.initialPivot = window.pivot;
		this.initialWorld = implicit.startWorldPos();
		this.plane = window.getPlane();
	}
	
	@Override
	public void init() throws GrabDroppedException {
	}
	
	@Override
	public void release(boolean force) throws GrabDroppedException {
	}
	
	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up, float yRot, float xRot) throws GrabDroppedException {
		if(!window.isValid()) this.drop();
		
		wlc.cursorShape = CursorShape.ALL_RESIZE;
		
		Intersection intersect = plane.intersect(pos, view);
		if(intersect == null) return;
		
		Vec3 worldDiff = initialWorld.subtract(intersect.world());
		
		Vec3 diff = intersect.local().subtract(initialLocal);
		snapActive = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL);
		if(snapActive) {
			double minDist = 0.25;
			if(max3(Math.abs(worldDiff.x), Math.abs(worldDiff.y), Math.abs(worldDiff.z)) < minDist) {
				diff = Vec3.ZERO;
			}
			else if(Math.abs(diff.x) > Math.abs(diff.y)) {
				diff = new Vec3(diff.x, 0, 0);
			}
			else {
				diff = new Vec3(0, diff.y, 0);
			}
		}
		
		window.pivot = initialPivot.add(plane.localX.scale(diff.x)).add(plane.localY.scale(diff.y));
	}
	
	private static double max3(double a, double b, double c) {
		return Math.max(a, Math.max(b, c));
	}
	
}
