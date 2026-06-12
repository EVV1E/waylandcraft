package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.render.IMyItemFrameRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;

@Mixin(ItemFrameRenderer.class)
public class ItemFrameRendererMixin {
	
	@Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	public void submitItem(ItemStackRenderState itemStackRenderState, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outlineColor, @Local ItemFrameRenderState itemFrameRenderState) {
		WLCToplevel toplevel = ((IMyItemFrameRenderState) itemFrameRenderState).getToplevel();
		
		if(toplevel == null) {
			itemStackRenderState.submit(poseStack, collector, light, overlay, outlineColor);
			return;
		}
		
		WaylandCraft.instance.windowInItemFrameRenderer.render(toplevel, poseStack, collector);
	}
	
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	public void extractRenderState(ItemFrame itemFrame, ItemFrameRenderState itemFrameRenderState, float f, CallbackInfo info) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(itemFrame.getItem());
		((IMyItemFrameRenderState) itemFrameRenderState).setToplevel(toplevel);
	}
	
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelResolver;updateForItemFrame(Lnet/minecraft/client/renderer/block/BlockModelRenderState;ZZ)V"))
	public void changeItemFrameModel(BlockModelResolver resolver, BlockModelRenderState renderState, boolean glowFrame, boolean map, @Local ItemFrameRenderState itemFrameRenderState) {
		WLCToplevel toplevel = ((IMyItemFrameRenderState) itemFrameRenderState).getToplevel();
		if(toplevel != null) {
			resolver.updateForItemFrame(renderState, glowFrame, true);
			return;
		}
		
		resolver.updateForItemFrame(renderState, glowFrame, map);
	}
	
}
