package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.opengl.FrameBufferCache;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface IGlDeviceMixin {

	@Invoker("frameBufferCache")
	FrameBufferCache invokeFrameBufferCache();

}
