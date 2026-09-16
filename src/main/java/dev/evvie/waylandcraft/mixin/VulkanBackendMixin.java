package dev.evvie.waylandcraft.mixin;

import java.util.Collection;
import java.util.Set;

import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.vulkan.VulkanHelper;

@Mixin(VulkanBackend.class)
public class VulkanBackendMixin {
	
	@WrapOperation(
			method = "createDevice(JLcom/mojang/blaze3d/shaders/ShaderSource;Lcom/mojang/blaze3d/shaders/GpuDebugOptions;Ljava/lang/Runnable;)Lcom/mojang/blaze3d/systems/GpuDevice;",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanBackend;createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;")
	)
	private VkDevice checkMoreVulkanExtensions(Collection<String> deviceExtensions, VulkanPhysicalDevice physicalDevice, Set<VulkanFeature> features, Operation<VkDevice> original) {
		boolean hasNecessaryExtensions = true;
		for(String extension : VulkanHelper.NECESSARY_VULKAN_EXTENSIONS) {
			if(!physicalDevice.hasDeviceExtension(extension)) {
				WaylandCraftCommon.LOGGER.error("Vulkan physical device is missing necessary extension " + extension + "!");
				hasNecessaryExtensions = false;
				break;
			}
		}
		
		if(hasNecessaryExtensions) {
			for(String extension : VulkanHelper.NECESSARY_VULKAN_EXTENSIONS) {
				deviceExtensions.add(extension);
				System.out.println("ADDED EXTENSION " + extension);
			}
		}
		
		return original.call(deviceExtensions, physicalDevice, features);
	}
	
}
