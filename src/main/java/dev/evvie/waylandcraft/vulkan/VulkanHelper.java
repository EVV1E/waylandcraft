package dev.evvie.waylandcraft.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDeviceDrmPropertiesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;

public class VulkanHelper {
	
	// See VulkanGpuTextureMixin, VulkanGpuTextureViewMixin and BufferTexture
	public static final ScopedValue<Integer> VULKAN_GPU_TEXTURE_CREATE_FORMAT_OVERRIDE = ScopedValue.newInstance();
	
	public static record DrmNodeId(int major, int minor) {}
	
	public static DrmNodeId getRenderNodeId() {
		VulkanDevice device = (VulkanDevice) RenderSystem.getDevice().backend;
		try(MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceProperties2 props = VkPhysicalDeviceProperties2.calloc(stack).sType$Default();
			VkPhysicalDeviceDrmPropertiesEXT drmProps = VkPhysicalDeviceDrmPropertiesEXT.calloc(stack).sType$Default();
			props.pNext(drmProps);
			VK12.vkGetPhysicalDeviceProperties2(device.vkDevice().getPhysicalDevice(), props);
			
			if(!drmProps.hasRender()) return null;
			return new DrmNodeId((int) drmProps.renderMajor(), (int) drmProps.renderMinor());
		}
	}
	
}
