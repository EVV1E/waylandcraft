package dev.evvie.waylandcraft.vulkan;

import java.util.ArrayList;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDrmFormatModifierPropertiesEXT;
import org.lwjgl.vulkan.VkDrmFormatModifierPropertiesListEXT;
import org.lwjgl.vulkan.VkFormatProperties2;
import org.lwjgl.vulkan.VkPhysicalDeviceDrmPropertiesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat;

public class VulkanHelper {
	
	// See VulkanGpuTextureMixin, VulkanGpuTextureViewMixin and BufferTexture
	public static final ScopedValue<Integer> VULKAN_GPU_TEXTURE_CREATE_FORMAT_OVERRIDE = ScopedValue.newInstance();
	
	public static record DrmNodeId(int major, int minor) {}
	
	public static VulkanDevice getVulkanDevice() {
		return (VulkanDevice) RenderSystem.getDevice().backend;
	}
	
	public static DrmNodeId getRenderNodeId(VulkanDevice device) {
		try(MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceProperties2 props = VkPhysicalDeviceProperties2.calloc(stack).sType$Default();
			VkPhysicalDeviceDrmPropertiesEXT drmProps = VkPhysicalDeviceDrmPropertiesEXT.calloc(stack).sType$Default();
			props.pNext(drmProps);
			VK12.vkGetPhysicalDeviceProperties2(device.vkDevice().getPhysicalDevice(), props);
			
			if(!drmProps.hasRender()) return null;
			return new DrmNodeId((int) drmProps.renderMajor(), (int) drmProps.renderMinor());
		}
	}
	
	public static ArrayList<DmabufFormat> queryDmabufFormats(VulkanDevice device) {
		ArrayList<DmabufFormat> formats = new ArrayList<DmabufFormat>();
		DrmVulkanFormat knownVKFormats[] = DrmVulkanFormat.FORMATS;
		for(DrmVulkanFormat format : knownVKFormats) {
			System.out.println(format.getFourccString() + ": " + format.toString());
			
			ArrayList<DrmModifiersInfo> mods = queryFormatDrmModifiers(device, format.vkFormat());
			for(DrmModifiersInfo info : mods) {
				System.out.println(String.format("  modifier 0x%016X (num_planes=%d)", info.modifier, info.numPlanes));
				formats.add(new DmabufFormat(format.fourcc(), info.modifier()));
			}
		}
		return formats;
	}
	
	public static record DrmModifiersInfo(long modifier, int numPlanes) {}
	
	public static ArrayList<DrmModifiersInfo> queryFormatDrmModifiers(VulkanDevice device, int vkFormat) {
		ArrayList<DrmModifiersInfo> mods = new ArrayList<DrmModifiersInfo>();
		
		try(MemoryStack stack = MemoryStack.stackPush()) {
			VkFormatProperties2 props = VkFormatProperties2.calloc(stack).sType$Default();
			VkDrmFormatModifierPropertiesListEXT modifierPropsList = VkDrmFormatModifierPropertiesListEXT.calloc(stack).sType$Default();
			props.pNext(modifierPropsList);
			
			// First do a query with the zero'd VkDrmFormatModifierPropertiesListEXT struct, i.e. pDrmFormatModifierProperties == NULL to find out how many modifiers there are (returned in drmFormatModifierCount)
			VK12.vkGetPhysicalDeviceFormatProperties2(device.vkDevice().getPhysicalDevice(), vkFormat, props);
			
			// Create a buffer large enough to hold that many VkDrmFormatModifierPropertiesEXT structs and write the address to modifiersPropsList
			int count = modifierPropsList.drmFormatModifierCount();
			long addr = stack.ncalloc(VkDrmFormatModifierPropertiesEXT.ALIGNOF, count, VkDrmFormatModifierPropertiesEXT.SIZEOF);
			VkDrmFormatModifierPropertiesEXT.Buffer modifiersBuf = VkDrmFormatModifierPropertiesEXT.create(addr, count);
			MemoryUtil.memPutAddress(modifierPropsList.address() + VkDrmFormatModifierPropertiesListEXT.PDRMFORMATMODIFIERPROPERTIES, modifiersBuf.address());
			
			// Now the max count and address of the buffer are in place. Query the actual formats
			VK12.vkGetPhysicalDeviceFormatProperties2(device.vkDevice().getPhysicalDevice(), vkFormat, props);
			count = modifierPropsList.drmFormatModifierCount();
			
			for(int i = 0; i < count; i++) {
				VkDrmFormatModifierPropertiesEXT modifier = modifiersBuf.get(i);
				mods.add(new DrmModifiersInfo(modifier.drmFormatModifier(), modifier.drmFormatModifierPlaneCount()));
			}
		}
		return mods;
	}
	
}
