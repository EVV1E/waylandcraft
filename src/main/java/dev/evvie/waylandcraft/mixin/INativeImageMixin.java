package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.platform.NativeImage;

@Mixin(NativeImage.class)
public interface INativeImageMixin {
	
	@Invoker("<init>")
	static NativeImage createImage(NativeImage.Format format, int width, int height, boolean useStbFree, long ptr) {
		throw new AssertionError();
	}
	
}
