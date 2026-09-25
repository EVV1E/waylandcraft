package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;

@Mixin(ItemInHandRenderer.class)
public interface IItemInHandRendererMixin {
	
	@Invoker("renderPlayerArm")
	void invokeRenderPlayerArm(PoseStack poseStack, MultiBufferSource buffers, int light, float handHeight, float attack, HumanoidArm humanoidArm);
	
}
