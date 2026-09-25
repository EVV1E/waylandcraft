package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraft.PointerCaptureOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	
	@Inject(method = "onPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"), cancellable = true)
	public void onButton(long windowHandle, int button, int action, int modifiers, CallbackInfo info) {
		if(WaylandCraft.instance.onButtonPress(windowHandle, button, action, modifiers)) info.cancel();
	}
	
	@Inject(method = "onPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0))
	public void onButtonMaybeOverlay(long windowHandle, int button, int action, int modifiers, CallbackInfo info) {
		if(Minecraft.getInstance().getOverlay() instanceof PointerCaptureOverlay && WaylandCraft.instance.pointerCapture != null) {
			WaylandCraft.instance.onButtonPress(windowHandle, button, action, modifiers);
		}
	}
	
	@Inject(method = "onScroll", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;", ordinal = 0), cancellable = true)
	public void onScroll(long windowHandle, double scrollX, double scrollY, CallbackInfo info) {
		if(WaylandCraft.instance.onScroll(windowHandle, scrollX, scrollY)) info.cancel();
	}
	
	@Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0))
	public void onScrollMaybeOverlay(long windowHandle, double scrollX, double scrollY, CallbackInfo info) {
		if(Minecraft.getInstance().getOverlay() instanceof PointerCaptureOverlay && WaylandCraft.instance.pointerCapture != null) {
			WaylandCraft.instance.onScroll(windowHandle, scrollX, scrollY);
		}
	}
	
	@WrapOperation(method = "grabMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;setAll()V"))
	private void stopSetAllKeys(Operation<Void> original) {
		if(!WaylandCraft.PointerCaptureOverlay.stopKeyMappingSetAll) original.call();
	}
	
	@Shadow public double accumulatedDX;
	@Shadow public double accumulatedDY;
	
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	public void onTurnPlayer(double timeDelta, CallbackInfo info) {
		if(WaylandCraft.instance.onMouseTurn(accumulatedDX, accumulatedDY)) info.cancel();
	}
	
}
