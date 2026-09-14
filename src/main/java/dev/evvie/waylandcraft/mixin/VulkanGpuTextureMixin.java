package dev.evvie.waylandcraft.mixin;

import org.lwjgl.vulkan.VkImageCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;

import dev.evvie.waylandcraft.vulkan.VulkanHelper;

@Mixin(VulkanGpuTexture.class)
public class VulkanGpuTextureMixin {
	
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkImageCreateInfo;format(I)Lorg/lwjgl/vulkan/VkImageCreateInfo;"))
	private VkImageCreateInfo overrideFormat(VkImageCreateInfo imageCreateInfo, int inFormat, Operation<VkImageCreateInfo> original) {
		return original.call(imageCreateInfo, VulkanHelper.VULKAN_GPU_TEXTURE_CREATE_FORMAT_OVERRIDE.orElse(inFormat));
	}
	
}
