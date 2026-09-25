package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

// Frames holding a live window use the flat map frame model, like frames holding a map.
// The window itself is drawn from RenderItemInFrameEvent (see WaylandCraft).
@Mixin(ItemFrameRenderer.class)
public class ItemFrameRendererMixin {
	
	@Shadow @Final private static ModelResourceLocation MAP_FRAME_LOCATION;
	@Shadow @Final private static ModelResourceLocation GLOW_MAP_FRAME_LOCATION;
	
	@Inject(method = "getFrameModelResourceLoc", at = @At("HEAD"), cancellable = true)
	public void useMapFrameForWindows(ItemFrame itemFrame, ItemStack itemStack, CallbackInfoReturnable<ModelResourceLocation> info) {
		if(WaylandCraft.getToplevel(itemStack) == null) return;
		
		boolean glow = itemFrame.getType() == EntityType.GLOW_ITEM_FRAME;
		info.setReturnValue(glow ? GLOW_MAP_FRAME_LOCATION : MAP_FRAME_LOCATION);
	}
	
}
