package dev.evvie.waylandcraft.displays;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.math.WorldPlane;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractWindowDisplay {
	
	// World position of window
	public Vec3 pivot = new Vec3(0, 0, 0);
	
	// Window facing direction normal
	protected Vec3 normal = new Vec3(0, 0, 1);
	
	// Window orientation downwards vector, has to be orthogonal to `normal` and normalized
	protected Vec3 down = new Vec3(0, -1, 0);
	
	protected int width;
	protected int height;
	
	protected int geometryX = 0;
	protected int geometryY = 0;
	
	private float pixelScale;
	
	public AbstractWindowDisplay() {
	}
	
	public abstract boolean isValid();
	public abstract void updateGeometry();
	
	public abstract void renderFramebuffer(PoseStack poseStack, SubmitNodeCollector collector, Vec3 origin, Vec3 spanX, Vec3 spanY);
	public abstract @Nullable FramebufferRenderable getFramebuffer();
	
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
	
	public void setPixelScale(float scale) {
		this.pixelScale = scale;
	}
	
	public Vec3 localX() {
		return right().scale(pixelScale);
	}
	
	public Vec3 localY() {
		return down.scale(pixelScale);
	}
	
	// World coordinates of the window geometry origin
	public Vec3 origin() {
		return pivot.add(localX().scale(-width/2)).add(localY().scale(-height/2));
	}
	
	public WorldPlane getPlane() {
		return new WorldPlane(origin(), localX(), localY(), normal);
	}
	
	public Vec3 localToWorld(double x, double y, double z) {
		return getPlane().localToWorld(x, y, z);
	}
	
	public void moveOrigin(Vec3 pos) {
		pivot = pos.add(localX().scale(width/2)).add(localY().scale(height/2));
	}
	
	public DisplayProperties getProperties() {
		return new DisplayProperties(pivot, normal, down, width, height, geometryX, geometryY, pixelScale);
	}
	
	public void applyProperties(DisplayProperties properties) {
		this.pivot = properties.pivot();
		this.normal = properties.normal();
		this.down = properties.down();
		this.width = properties.width();
		this.height = properties.height();
		this.geometryX = properties.geometryX();
		this.geometryY = properties.geometryY();
		this.pixelScale = properties.pixelScale();
	}
	
	public void render(LevelRenderContext ctx) {
		FramebufferRenderable framebuffer = getFramebuffer();
		if(framebuffer == null) return;
		
		updateGeometry();
		
		int xoff = framebuffer.getXOff();
		int yoff = framebuffer.getYOff();
		int bufWidth = framebuffer.getWidth();
		int bufHeight = framebuffer.getHeight();
		
		Vec3 localX = localX();
		Vec3 localY = localY();
		
		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		Vec3 originRel = origin().subtract(cameraPos);
		
		Vec3 bufOffset = localX.scale(-xoff - geometryX).add(localY.scale(-yoff - geometryY));
		
		PoseStack poseStack = ctx.poseStack();
		poseStack.pushPose();
		poseStack.translate(originRel.x, originRel.y, originRel.z);
		renderFramebuffer(poseStack, ctx.submitNodeCollector(), bufOffset, localX.scale(bufWidth), localY.scale(bufHeight));
		poseStack.popPose();
	}
	
	/* Transform absolute world coordinates to surface-local pixel coordinates relative to geometry (0, 0)
	 * 
	 * The resulting vector is the (x, y) pixel location and the z value is the block distance normal to the plane.
	 */
	public Vec3 worldToLocal(Vec3 in) {
		return getPlane().worldToLocal(in);
	}
	
}
