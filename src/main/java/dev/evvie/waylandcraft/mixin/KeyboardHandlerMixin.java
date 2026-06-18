package dev.evvie.waylandcraft.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	
	@Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(Lnet/minecraft/client/input/KeyEvent;)Lcom/mojang/blaze3d/platform/InputConstants$Key;", ordinal = 1), cancellable = true)
	public void onPressInGame(long windowHandle, int action, KeyEvent event, CallbackInfo info) {
		int scancode = WaylandCraft.correctScancode(event.scancode());
		
		if(Minecraft.getInstance().level == null) return;
		if(Minecraft.getInstance().screen != null) return;
		
		if(WaylandCraft.instance.onKeyPress(windowHandle, event.key(), scancode, action, event.modifiers())) info.cancel();
	}
	
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = false)
	public void onPressGlobal(long windowHandle, int action, KeyEvent event, CallbackInfo info) {
		if(WaylandCraft.instance.bridge == null) return;
		
		int scancode = WaylandCraft.correctScancode(event.scancode());
		if(action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
		
		WaylandCraft.instance.bridge.internalKeyUpdate(scancode, action == GLFW.GLFW_PRESS);
	}
	
}
