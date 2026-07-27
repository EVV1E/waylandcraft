package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;

@Mixin(GpuDevice.class)
public interface IGpuDeviceMixin {

	@Accessor("backend")
	GpuDeviceBackend getBackend();

}
