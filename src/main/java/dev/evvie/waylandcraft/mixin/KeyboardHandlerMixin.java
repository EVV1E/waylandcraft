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

	// Cancelling deep inside keyPress (at a specific getKey() call ordinal)
	// only skips whatever vanilla logic comes *after* that point in the
	// method -- it does NOT retroactively undo InputConstants/KeyMapping
	// state that vanilla may have already recorded earlier in the same
	// method call, unconditionally, before our injection point is ever
	// reached. That let capture-consumed keys (e.g. 'O') still register as
	// "down" for polling-based keybinds (KeyMapping#consumeClick, used by
	// things like the social interactions screen) even though our capture
	// logic thought it had swallowed the press. Injecting at HEAD and
	// cancelling there guarantees vanilla never processes the event at all.
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	public void onPress(long windowHandle, int action, KeyEvent event, CallbackInfo info) {
		int scancode = WaylandCraft.correctScancode(event.scancode());

		if(WaylandCraft.instance.bridge != null && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE)) {
			WaylandCraft.instance.bridge.internalKeyUpdate(scancode, action == GLFW.GLFW_PRESS);
		}

		if(Minecraft.getInstance().level == null) return;
		if(Minecraft.getInstance().gui.screen() != null) return;

		if(WaylandCraft.instance.onKeyPress(windowHandle, event.key(), scancode, action, event.modifiers())) info.cancel();
	}
	
}
