package dev.evvie.waylandcraft.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.evvie.waylandcraft.bridge.WLCToplevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

public class WindowInItemFrameRenderer {
	
	public void render(WLCToplevel toplevel, PoseStack poseStack, SubmitNodeCollector collector) {
		WindowFramebuffer buf = toplevel.getFramebuffer();
		if(buf == null) return;
		
		poseStack.pushPose();
		poseStack.translate(1.0f, 1.0f, -0.01f);
		poseStack.scale(2.0f, 2.0f, 1.0f);
		poseStack.rotateAround(Axis.ZP.rotationDegrees(180), 0, 0, 0);
		
		int width = toplevel.geometry.width();
		int height = toplevel.geometry.height();
		int resolution;
		
		if(width > height) {
			resolution = width;
		}
		else {
			resolution = height;
		}
		
		float scale = 1.0f / resolution;
		poseStack.scale(scale, scale, 1.0f);
		
		int x = -buf.getXOff() - toplevel.geometry.x();
		int y = -buf.getYOff() - toplevel.geometry.y();
		int w = buf.getWidth();
		int h = buf.getHeight();
		
		x += resolution / 2 - width / 2;
		y += resolution / 2 - height / 2;
		
		RenderUtils.renderFramebuffer(buf, poseStack, collector, true, new Vec3(x, y, 0), new Vec3(w, 0, 0), new Vec3(0, h, 0));
		
		poseStack.popPose();
	}
	
}
