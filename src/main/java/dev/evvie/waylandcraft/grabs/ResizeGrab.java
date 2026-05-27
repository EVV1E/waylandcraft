package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WindowDisplay.DisplayHitResult;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.grabs.PointerGrabMap.ImplicitGrab;
import net.minecraft.world.phys.Vec3;

public class ResizeGrab extends PointerGrab {
	
	private final WindowDisplay window;
	private final WLCToplevel toplevel;
	private final Vec3 initialDisplayPos;
	
	private Vec3 initialSurfaceLocal;
	
	private final ResizeEdges edges;
	private final SurfaceGeometry initialGeometry;
	
	private int width;
	private int height;
	
	public ResizeGrab(ImplicitGrab implicit, int edges) {
		super(implicit.button());
		this.window = implicit.window();
		this.toplevel = (WLCToplevel) window.window;
		this.initialSurfaceLocal = implicit.startSurfaceLocal();
		this.edges = ResizeEdges.forNumber(edges);
		this.initialGeometry = window.window.geometry;
		this.width = initialGeometry.width();
		this.height = initialGeometry.height();
		this.initialDisplayPos = window.origin();
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
		
		DisplayHitResult hitResult = window.intersect(pos, view);
		if(hitResult == null) return;
		
		int horizontal = edges.horizontalMult();
		int vertical = edges.verticalMult();
		
		Vec3 surfLocalInitial = window.worldToLocal(initialDisplayPos);
		Vec3 diff = hitResult.surfaceLocalOrigin.subtract(surfLocalInitial).subtract(initialSurfaceLocal);
		int dx = (int) diff.x * horizontal;
		int dy = (int) diff.y * vertical;
		
		int nwidth = initialGeometry.width() + dx;
		int nheight = initialGeometry.height() + dy;
		
		if(nwidth == width && nheight == height) return;
		if(nwidth < 1 || nheight < 1 || nwidth > 10000 || nheight > 10000) return;
		
		window.moveOrigin(initialDisplayPos);
		if(horizontal < 0) {
			window.pivot = window.pivot.add(window.localX().scale(-dx));
		}
		if(vertical < 0) {
			window.pivot = window.pivot.add(window.localY().scale(-dy));
		}
		
		wlc.bridge.resizeToplevelInteractive(toplevel, nwidth, nheight);
	}
	
	private static enum ResizeEdges {
		
		NONE, TOP, BOTTOM, LEFT, TOP_LEFT, BOTTOM_LEFT, RIGHT, TOP_RIGHT, BOTTOM_RIGHT;
		
		/* I know these are supposed to be bitmasks. Too bad! */
		
		public static ResizeEdges forNumber(int num) {
			switch(num) {
			case 1: return TOP;
			case 2: return BOTTOM;
			case 4: return LEFT;
			case 5: return TOP_LEFT;
			case 6: return BOTTOM_LEFT;
			case 8: return RIGHT;
			case 9: return TOP_RIGHT;
			case 10: return BOTTOM_RIGHT;
			default: return NONE;
			}
		}
		
		public int horizontalMult() {
			switch(this) {
			case RIGHT: return 1;
			case TOP_RIGHT: return 1;
			case BOTTOM_RIGHT: return 1;
			case LEFT: return -1;
			case TOP_LEFT: return -1;
			case BOTTOM_LEFT: return -1;
			default: return 0;
			}
		}
		
		public int verticalMult() {
			switch(this) {
			case BOTTOM: return 1;
			case BOTTOM_RIGHT: return 1;
			case BOTTOM_LEFT: return 1;
			case TOP: return -1;
			case TOP_RIGHT: return -1;
			case TOP_LEFT: return -1;
			default: return 0;
			}
		}
		
	}
	
}
