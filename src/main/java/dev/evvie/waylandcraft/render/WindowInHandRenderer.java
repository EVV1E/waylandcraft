package dev.evvie.waylandcraft.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.Size;
import dev.evvie.waylandcraft.mixin.IItemInHandRendererMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class WindowInHandRenderer {
	
	public void render(PoseStack poseStack, SubmitNodeCollector collector, float attack, float handHeight, int light, HumanoidArm humanoidArm, ItemStack itemStack) {
		poseStack.pushPose();
		
		float h = humanoidArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		poseStack.translate(h * 0.125f, -0.125f, 0.0f);
		
		if (!Minecraft.getInstance().player.isInvisible()) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(h * 10.0f));
			renderPlayerArm(poseStack, collector, light, handHeight, attack, humanoidArm);
			poseStack.popPose();
		}
		
		poseStack.translate(h * 0.8f, handHeight * -0.6f - 0.275f, -0.85f);
		
		float sattack = Mth.sqrt(attack);
		float osci = Mth.sin(sattack * (float) Math.PI);
		float dx = -0.6f * osci;
		float dy = 0.55f * Mth.sin(sattack * (float) (Math.PI * 2));
		float dz = -0.6f * Mth.sin(attack * (float) Math.PI);
		poseStack.translate(h * dx, dy - 0.3f * osci, dz);
		poseStack.mulPose(Axis.XP.rotationDegrees(osci * -45.0f));
		poseStack.mulPose(Axis.YP.rotationDegrees(h * osci * -30.0f));
		
		renderWindow(poseStack, collector, h, light, itemStack);
		
		poseStack.popPose();
	}
	
	public void renderWindow(PoseStack poseStack, SubmitNodeCollector collector, float sideMult, int light, ItemStack itemStack) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(itemStack);
		if(toplevel == null) return;
		if(toplevel.framebuffer == null) return;
		
		float width = toplevel.geometry.width();
		float height = toplevel.geometry.height();
		
		Size size = WaylandCraft.instance.bridge.getOutputSize();
		float sWidth = size.width();
		float sHeight = size.height();
		
		float wscale;
		float hscale;
		
		/* The following math was established entirely through the use of intuitive guesswork and brute force.*/
		/* It does not work when the game is running at an aspect ratio < 1, but who's weird enough play like that? */
		
		float relW = (width / height) / (sWidth / sHeight);
		
		// window aspect ratio lesser than screen aspect ratio
		if(relW <= 1) {
			wscale = width / height;
			hscale = 1.0f;
			
			wscale /= (sWidth / sHeight);
			hscale /= (sWidth / sHeight);
		}
		else {
			wscale = 1.0f;
			hscale = height / width;
		}
		
		/* Move windows with small aspect ratio about in the hand */
		final float threshold = 0.5f;
		final float moveDist = 0.14f;
		float offset = (Math.min(wscale, threshold) - threshold) * (moveDist / threshold);
		poseStack.translate(offset * sideMult, 0, 0);
		
		/* Final transformations */
		final float scale = 0.6f;
		poseStack.scale(scale, scale, 1);
		poseStack.translate(-wscale / 2 * sideMult, hscale / 2, 0);
		poseStack.scale(wscale, hscale, 1);
		poseStack.translate(-0.5, -0.5, 0);
		
		Vec3 tl = new Vec3(0, 1, 0);
		Vec3 bl = new Vec3(0, 0, 0);
		Vec3 br = new Vec3(1, 0, 0);
		Vec3 tr = new Vec3(1, 1, 0);
		
		RenderUtils.renderFramebuffer(toplevel.framebuffer, poseStack, collector, false, tl, bl, br, tr);
	}
	
	public void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int light, float handHeight, float attack, HumanoidArm humanoidArm) {
		((IItemInHandRendererMixin) Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()).invokeRenderPlayerArm(poseStack, collector, light, handHeight, attack, humanoidArm);
	}
	
}
