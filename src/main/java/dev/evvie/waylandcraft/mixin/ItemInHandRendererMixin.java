package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.item.WindowItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"), cancellable = true)
	public void renderArmWithItem(
		AbstractClientPlayer player,
		float partialTicks,
		float yaw,
		InteractionHand interactionHand,
		float attack,
		ItemStack itemStack,
		float handHeight,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int light,
		CallbackInfo info,
		@Local LocalRef<HumanoidArm> humanoidArmRef
	) {
		if(!itemStack.is(WindowItem.WINDOW)) return;
		if(WaylandCraft.getToplevel(itemStack) == null) return;
		
		info.cancel();
		
		WaylandCraft.instance.windowInHandRenderer.render(poseStack, collector, attack, handHeight, light, humanoidArmRef.get(), itemStack);
	}
	
}
