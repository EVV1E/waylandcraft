package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// update() runs on RenderFrameEvent.Pre (see WaylandCraft), so only the pick hook
// remains. 1.21.1 picks in GameRenderer rather than Minecraft.
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	
	@Inject(method = "pick(F)V", at = @At("TAIL"))
	public void pick(float partialTicks, CallbackInfo info) {
		if(Minecraft.getInstance().player == null) return;
		
		HitResult result = Minecraft.getInstance().hitResult;
		Vec3 pos = Minecraft.getInstance().player.getEyePosition(partialTicks);
		
		WaylandCraft.instance.trueGameHitResult = result;
		
		WaylandCraft.instance.updatePointer();
		
		if(WaylandCraft.instance.overridePickBlock) {
			Minecraft.getInstance().hitResult = BlockHitResult.miss(pos, Direction.DOWN, BlockPos.containing(pos));
			Minecraft.getInstance().crosshairPickEntity = null;
		}
	}
	
}
