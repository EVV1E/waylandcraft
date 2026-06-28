package dev.evvie.waylandcraft.displays;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

public class RemoteDisplay extends AbstractWindowDisplay {
	
	public final WindowHandle handle;
	
	public RemoteDisplay(WindowHandle handle) {
		this.handle = handle;
	}
	
	@Override
	public boolean isValid() {
		return true;
	}
	
	@Override
	public void updateGeometry() {
	}
	
	public void setSize(int width, int height) {
		this.width = width;
		this.height = height;
	}
	
	@Override
	public void render(LevelRenderContext ctx) {
		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		
		PoseStack poseStack = ctx.poseStack();
		poseStack.pushPose();
		poseStack.translate(Vec3.ZERO.subtract(cameraPos));
		
		RenderUtils.renderBox(poseStack, ctx.submitNodeCollector(), pivot, 0xffffffff, 0.07f);
		
		Vec3 origin = origin();
		Vec3 spanX = localX().scale(width);
		Vec3 spanY = localY().scale(height);
		Vec3[] points = new Vec3[] {
				origin,
				origin.add(spanX),
				origin.add(spanX).add(spanY),
				origin.add(spanY),
				origin
		};
		RenderUtils.renderLineStrip(poseStack, ctx.submitNodeCollector(), points, 0xffffffff, 2.0f);
		
		poseStack.popPose();
	}
	
	@Override
	public void renderFramebuffer(PoseStack poseStack, SubmitNodeCollector collector, Vec3 origin, Vec3 spanX, Vec3 spanY) {
	}
	
	@Override
	public @Nullable FramebufferRenderable getFramebuffer() {
		return null;
	}
	
}
