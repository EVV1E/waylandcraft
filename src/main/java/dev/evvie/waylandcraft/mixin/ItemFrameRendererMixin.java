package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.render.IMyItemFrameRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;

@Mixin(ItemFrameRenderer.class)
public class ItemFrameRendererMixin {
	
	@WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void windowItemSubmitOverride(ItemStackRenderState itemStackRenderState, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outlineColor, Operation<Void> original, @Local ItemFrameRenderState renderState) {
		WLCToplevel toplevel = ((IMyItemFrameRenderState) renderState).getToplevel();
		
		if(toplevel == null) original.call(itemStackRenderState, poseStack, collector, light, overlay, outlineColor);
		else WaylandCraft.instance.windowInItemFrameRenderer.render(toplevel, poseStack, collector);
	}
	
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	public void addToplevelRenderState(ItemFrame itemFrame, ItemFrameRenderState renderState, float f, CallbackInfo info) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(itemFrame.getItem());
		((IMyItemFrameRenderState) renderState).setToplevel(toplevel);
	}
	
	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelResolver;updateForItemFrame(Lnet/minecraft/client/renderer/block/BlockModelRenderState;ZZ)V"), index = 2)
	private boolean changeModelWithWindowItem(boolean isMap, @Local ItemFrameRenderState renderState) {
		return ((IMyItemFrameRenderState) renderState).getToplevel() != null ? true : isMap;
	}
	
}
