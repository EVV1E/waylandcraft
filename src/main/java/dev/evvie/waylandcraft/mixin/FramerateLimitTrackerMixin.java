package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.FramerateLimitTracker;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
	
	@Shadow
	private int framerateLimit;
	
	@WrapMethod(method = "getFramerateLimit")
	public int disableFramerateLimiting(Operation<Integer> original) {
		return framerateLimit;
	}
	
}
