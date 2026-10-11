package dev.evvie.waylandcraft.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	
	@WrapMethod(method = "keyPress")
	private void handleKeyPress(long windowHandle, int action, KeyEvent event, Operation<Void> original) {
		if(windowHandle == Minecraft.getInstance().getWindow().handle()) {
			int scancode = WaylandCraft.correctScancode(event.scancode());
			updateInternal(scancode, action);
			
			boolean canCapture = Minecraft.getInstance().level != null && Minecraft.getInstance().screen == null;
			if(canCapture && WaylandCraft.instance.onKeyPress(windowHandle, event.key(), scancode, action, event.modifiers())) {
				// Key press has been consumed
				return;
			}
		}
		
		original.call(windowHandle, action, event);
	}
	
	private void updateInternal(int scancode, int action) {
		if(WaylandCraft.instance.bridge == null) return;
		if(action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
		
		WaylandCraft.instance.bridge.internalKeyUpdate(scancode, action == GLFW.GLFW_PRESS);
	}
	
}
