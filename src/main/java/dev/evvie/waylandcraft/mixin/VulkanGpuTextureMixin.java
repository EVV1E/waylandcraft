package dev.evvie.waylandcraft.mixin;

import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;

import dev.evvie.waylandcraft.vulkan.VulkanHelper;

@Mixin(VulkanGpuTexture.class)
public class VulkanGpuTextureMixin {
	
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaCreateImage(JLorg/lwjgl/vulkan/VkImageCreateInfo;Lorg/lwjgl/util/vma/VmaAllocationCreateInfo;Ljava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/util/vma/VmaAllocationInfo;)I"))
	private int overrideCreateImage(long allocator, VkImageCreateInfo imageCreateInfo, VmaAllocationCreateInfo allocCreateInfo, LongBuffer imageHandleOut, PointerBuffer allocationOut, VmaAllocationInfo allocationInfoOut, Operation<Integer> original) {
		long vkImageOverride = VulkanHelper.VULKAN_GPU_TEXTURE_IMAGE_CREATE_OVERRIDE.orElse(MemoryUtil.NULL);
		if(vkImageOverride != MemoryUtil.NULL) {
			imageHandleOut.put(0, vkImageOverride);
			allocationOut.put(0, MemoryUtil.NULL);
			return VK12.VK_SUCCESS;
		}
		
		int originalFormat = imageCreateInfo.format();
		int format = VulkanHelper.VULKAN_GPU_TEXTURE_CREATE_FORMAT_OVERRIDE.orElse(originalFormat);
		imageCreateInfo.format(format);
		
		return original.call(allocator, imageCreateInfo, allocCreateInfo, imageHandleOut, allocationOut, allocationInfoOut);
	}
	
}
