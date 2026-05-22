package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.render.WindowTranslucencyHotfix;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	
	@Inject(method = "runTick", at = @At(value = "INVOKE_STRING", target = "Lcom/mojang/blaze3d/platform/Window;setErrorSection(Ljava/lang/String;)V", args = "ldc=Post render"))
	public void updateRunTick(boolean doTick, CallbackInfo info) {
		WaylandCraft.instance.update();
	}
	
	@Inject(method = "renderFrame", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", args = "ldc=present"))
	public void hotfixRenderFrame(boolean advanceGameTime, CallbackInfo info) {
		WindowTranslucencyHotfix.render();
	}
	
	@Inject(method = "pick", at = @At("TAIL"))
	public void pick(float partialTicks, CallbackInfo info) {
		HitResult result = Minecraft.getInstance().hitResult;
		Vec3 pos = Minecraft.getInstance().player.getEyePosition(partialTicks);
		
		WaylandCraft.instance.trueGameHitResult = result;
		if(WaylandCraft.instance.overridePickBlock) {
			Minecraft.getInstance().hitResult = BlockHitResult.miss(pos, Direction.DOWN, BlockPos.containing(pos));
			Minecraft.getInstance().crosshairPickEntity = null;
		}
	}
	
}
