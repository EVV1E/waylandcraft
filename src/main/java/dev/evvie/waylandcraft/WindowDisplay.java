package dev.evvie.waylandcraft;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class WindowDisplay {
	
	public final WLCAbstractWindow window;
	
	// World position of window
	public Vec3 pivot = new Vec3(0, 0, 0);
	
	// Window facing direction normal
	private Vec3 normal = new Vec3(0, 0, 1);
	
	// Window orientation downwards vector, has to be orthogonal to `normal` and normalized
	private Vec3 down = new Vec3(0, -1, 0);

	public double anchorDistance = 2.0;

	private int width;
	private int height;
	
	public WindowDisplay(WLCAbstractWindow window) {
		this.window = window;
		this.updateGeometry();
	}
	
	public boolean isValid() {
		return window.isAlive() && window.framebuffer != null && window.framebuffer.isValid();
	}
	
	public void rotate(Vec3 normal, Vec3 down) {
		this.normal = normal;
		this.down = down;
	}
	
	public Vec3 normal() {
		return normal;
	}
	
	public Vec3 down() {
		return down;
	}
	
	public Vec3 right() {
		return normal.cross(down);
	}
	
	public float pixelScale() {
		return 1.0f / WaylandCraft.instance.settings.getPixelsPerBlock();
	}
	
	public Vec3 localX() {
		return right().scale(pixelScale());
	}
	
	public Vec3 localY() {
		return down.scale(pixelScale());
	}
	
	// World coordinates of the origin of the root surface surface-local coordinate space
	public Vec3 origin() {
		return pivot.add(localX().scale(-width/2)).add(localY().scale(-height/2));
	}
	
	public Vec3 localToWorld(double x, double y, double z) {
		Vec3 origin = origin();
		Vec3 localX = localX();
		Vec3 localY = localY();
		return origin.add(localX.scale(x)).add(localY.scale(y)).add(normal.scale(z));
	}
	
	public void moveOrigin(Vec3 pos) {
		pivot = pos.add(localX().scale(width/2)).add(localY().scale(height/2));
	}
	
	public void updateGeometry() {
		width = window.geometry.width();
		height = window.geometry.height();
	}
	
	public void render(LevelRenderContext ctx) {
		if(window.framebuffer == null) return;
		updateGeometry();
		
		int xoff = window.framebuffer.getXOff();
		int yoff = window.framebuffer.getYOff();
		int bufWidth = window.framebuffer.getWidth();
		int bufHeight = window.framebuffer.getHeight();

		Vec3 localX = localX();
		Vec3 localY = localY();

		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		Vec3 originRel = origin().subtract(cameraPos);

		Vec3 bufOffset = localX.scale(-xoff).add(localY.scale(-yoff));

		Vec3 tl = bufOffset;
		Vec3 bl = bufOffset.add(localY.scale(bufHeight));
		Vec3 br = bl.add(localX.scale(bufWidth));
		Vec3 tr = tl.add(localX.scale(bufWidth));
		
		PoseStack poseStack = ctx.poseStack();
		poseStack.pushPose();
		poseStack.translate(originRel.x, originRel.y, originRel.z);
		RenderUtils.renderFramebuffer(window.framebuffer, poseStack, ctx.submitNodeCollector(), true, tl, bl, br, tr);
		poseStack.popPose();
	}
	
	/* Transform absolute world coordinates to surface-local pixel coordinates relative to toplevel (0, 0)
	 * 
	 * The resulting vector is the (x, y) pixel location and the z value is the block distance normal to the plane.
	 */
	public Vec3 worldToLocal(Vec3 in) {
		Vec3 origin = origin();
		Vec3 localX = localX();
		Vec3 localY = localY();
		
		// World coordinates relative to the origin of this window
		Vec3 world = in.subtract(origin);
		
		Matrix3d matrix = new Matrix3d(
			localX.x, localX.y, localX.z, // Column 0
			localY.x, localY.y, localY.z, // Column 1
			normal.x, normal.y, normal.z  // Column 2
		);
		matrix.invert();
		
		Vector3d result = matrix.transform(new Vector3d(world.x, world.y, world.z));
		return new Vec3(result.x, result.y, result.z);
	}
	
	/* Perform ray-window plane intersection
	 * `dir` must be normalized.
	 */
	public DisplayHitResult intersect(Vec3 pos, Vec3 dir) {
		double p1 = pivot.subtract(pos).dot(normal);
		double p2 = dir.dot(normal);
		
		// Avoid division by zero
		if(p2 == 0) return null;
		
		double t = p1 / p2;
		
		// Intersection happens behind the camera
		if(t < 0) return null;
		
		Vec3 hitPos = pos.add(dir.scale(t));
		Vec3 localCoords = worldToLocal(hitPos);
		
		WLCSurface hitSurface = null;
		Vec3 localCoordsRelative = null;
		
		for(WLCSurface surface = window.getSurfaceTreeLast(); surface != null; surface = surface.getPrevChild()) {
			Vec3 rel = localCoords.subtract(surface.xSubpos, surface.ySubpos, 0);
			
			int width = surface.width();
			int height = surface.height();
			
			if(rel.x < 0 || rel.y < 0 || rel.x > width || rel.y > height) {
				continue;
			}
			
			if(WaylandCraft.instance.bridge.inputRegionContains(surface, rel.x, rel.y)) {
				hitSurface = surface;
				localCoordsRelative = rel;
				break;
			}
		}
		
		// Flip z-coordinate when on the window backside
		double dist = t;
		if(p2 > 0) dist *= -1;
		
		return new DisplayHitResult(this, hitSurface, hitPos, localCoords, localCoordsRelative, dist);
	}

	public void adjustAnchorDistance(double delta) {
		this.anchorDistance = Math.clamp(this.anchorDistance + delta * 0.1d, 0.5d, 20d);
	}
	
	public void anchorToPosView(Vec3 pos, Vec3 look, Vec3 up) {
		this.pivot = pos.add(look.scale(this.anchorDistance));
		this.rotate(look.reverse(), up.reverse());
	}
	
	public void anchorToCamera(Camera camera) {
		anchorToPosView(camera.position(), new Vec3(camera.forwardVector()), new Vec3(camera.upVector()));
	}
	
	public void anchorToEntity(Entity entity) {
		anchorToPosView(WaylandCraftUtils.getPosition(entity), WaylandCraftUtils.getLookVector(entity), WaylandCraftUtils.getUpVector(entity));
	}
	
	public void trySnapWorld(Vec3 pos, Vec3 view, float yRot, boolean center) {
		BlockHitResult hitResult = Minecraft.getInstance().level.clip(new ClipContext(pos, pos.add(view.scale(32.0)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
		if(hitResult.getType() != HitResult.Type.BLOCK) return;
		
		Direction blockNormal = hitResult.getDirection();
		Direction viewDirection = Direction.fromYRot(yRot);
		
		Direction downDirection = Direction.DOWN;
		if(blockNormal.equals(Direction.UP)) {
			downDirection = viewDirection.getOpposite();
		}
		else if(blockNormal.equals(Direction.DOWN)) {
			downDirection = viewDirection;
		}
		
		this.rotate(blockNormal.getUnitVec3(), downDirection.getUnitVec3());
		this.pivot = hitResult.getLocation().add(blockNormal.getUnitVec3().scale(0.03));
		
		if(center) {
			double centerX = Math.floor(pivot.x) + 0.5;
			double centerY = Math.floor(pivot.y) + 0.5;
			double centerZ = Math.floor(pivot.z) + 0.5;
			
			if(blockNormal.getAxis().equals(Axis.X)) {
				this.pivot = new Vec3(pivot.x, centerY, centerZ);
			}
			else if(blockNormal.getAxis().equals(Axis.Y)) {
				this.pivot = new Vec3(centerX, pivot.y, centerZ);
			}
			else if(blockNormal.getAxis().equals(Axis.Z)) {
				this.pivot = new Vec3(centerX, centerY, pivot.z);
			}
		}
	}
	
	public void trySnapToOtherWindows(Vec3 pos, Vec3 view) {
		for(WindowDisplay display : WaylandCraft.instance.displays) {
			if(display == this) continue;
			if(!(display.window instanceof WLCToplevel)) continue;
			WLCToplevel toplevel = (WLCToplevel) display.window;
			
			DisplayHitResult result = display.intersect(pos, view);
			double hx = result.surfaceLocalOrigin.x();
			double hy = result.surfaceLocalOrigin.y();
			
			int left = toplevel.geometry.x();
			int top = toplevel.geometry.y();
			int right = left + toplevel.geometry.width();
			int bottom = top + toplevel.geometry.height();
			
			int myLeft = window.geometry.x();
			int myTop = window.geometry.y();
			int myRight = myLeft + window.geometry.width();
			int myBottom = myTop + window.geometry.height();
			
			double snapDistance = 0.2 * WaylandCraft.instance.settings.getPixelsPerBlock();
			
			// Right side snap
			if(hy >= top && hy <= bottom && hx >= right - snapDistance && hx <= right + snapDistance) {
				this.rotate(display.normal(), display.down());
				this.pivot = display.localToWorld(right + (myRight - myLeft) / 2 - myLeft, (bottom - top) / 2, 0);
				return;
			}
			// Left side snap
			else if(hy >= top && hy <= bottom && hx <= left + snapDistance && hx >= left - snapDistance) {
				this.rotate(display.normal(), display.down());
				this.pivot = display.localToWorld(left - (myRight - myLeft) / 2 - myLeft, (bottom - top) / 2, 0);
				return;
			}
			// Top side snap
			else if(hx >= left && hx <= right && hy >= top - snapDistance && hy <= top + snapDistance) {
				this.rotate(display.normal(), display.down());
				this.pivot = display.localToWorld((right - left) / 2, top - (myBottom - myTop) / 2 - myTop, 0);
				return;
			}
			// Bottom side snap
			else if(hx >= left && hx <= right && hy <= bottom + snapDistance && hy >= bottom - snapDistance) {
				this.rotate(display.normal(), display.down());
				this.pivot = display.localToWorld((right - left) / 2, bottom + (myBottom - myTop) / 2 - myTop, 0);
				return;
			}
		}
	}
	
	public static class DisplayHitResult {
		
		// WindowDisplay that was raycasted
		public final WindowDisplay target;
		
		// Surface that was hit, if any
		public final @Nullable WLCSurface surface;
		
		// World position
		public final Vec3 position;
		
		// Surface-local coordinates relative to WindowDisplay origin
		public final Vec3 surfaceLocalOrigin;
		
		// Surface-local coordinates relative to hit surface. Always guaranteed to not be null, if `surface` is non-null.
		public final @Nullable Vec3 surfaceLocalRelative;
		
		// Calculated distance
		public final double dist;
		
		public DisplayHitResult(WindowDisplay target, WLCSurface surface, Vec3 position, Vec3 surfaceLocalOrigin, Vec3 surfaceLocalRelative, double dist) {
			this.target = target;
			this.surface = surface;
			this.position = position;
			this.surfaceLocalOrigin = surfaceLocalOrigin;
			this.surfaceLocalRelative = surfaceLocalRelative;
			this.dist = dist;
		}
		
		public boolean isMiss() {
			return surface == null;
		}
		
		@Override
		public String toString() {
			return "{target=" + target + ", surface=" + surface + ", position=" + position + ", local=" + surfaceLocalOrigin + ", relative=" + surfaceLocalRelative + ", dist=" + dist + "}";
		}
		
	}
	
}
