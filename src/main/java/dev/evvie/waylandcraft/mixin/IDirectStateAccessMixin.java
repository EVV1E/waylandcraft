package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.opengl.DirectStateAccess;

@Mixin(DirectStateAccess.class)
public interface IDirectStateAccessMixin {
	
	@Invoker
	void invokeBlitFrameBuffers(
			final int source,
			final int dest,
			final int srcX0,
			final int srcY0,
			final int srcX1,
			final int srcY1,
			final int dstX0,
			final int dstY0,
			final int dstX1,
			final int dstY1,
			final int mask,
			final int filter
	);
	
}
