package dev.evvie.waylandcraft.mixin;

import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;

import dev.evvie.waylandcraft.vulkan.VulkanHelper;

@Mixin(VulkanGpuTextureView.class)
public class VulkanGpuTextureViewMixin {
	
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkImageViewCreateInfo;format(I)Lorg/lwjgl/vulkan/VkImageViewCreateInfo;"))
	private VkImageViewCreateInfo overrideFormat(VkImageViewCreateInfo imageViewCreateInfo, int inFormat, Operation<VkImageViewCreateInfo> original) {
		return original.call(imageViewCreateInfo, VulkanHelper.VULKAN_GPU_TEXTURE_CREATE_FORMAT_OVERRIDE.orElse(inFormat));
	}
	
}
