package dev.evvie.waylandcraft.vulkan;

import java.nio.LongBuffer;
import java.util.ArrayList;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTExternalMemoryDmaBuf;
import org.lwjgl.vulkan.EXTImageDrmFormatModifier;
import org.lwjgl.vulkan.KHRExternalMemoryFd;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDrmFormatModifierPropertiesEXT;
import org.lwjgl.vulkan.VkDrmFormatModifierPropertiesListEXT;
import org.lwjgl.vulkan.VkFormatProperties2;
import org.lwjgl.vulkan.VkImportMemoryFdInfoKHR;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryFdPropertiesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceDrmPropertiesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufPlane;

public class VulkanHelper {
	
	// See VulkanGpuTextureMixin, VulkanGpuTextureViewMixin and BufferTexture
	public static final ScopedValue<Integer> VULKAN_GPU_TEXTURE_CREATE_FORMAT_OVERRIDE = ScopedValue.newInstance();
	
	// See VulkanBackendMixin
	public static final String[] NECESSARY_VULKAN_EXTENSIONS = {
			KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
			EXTExternalMemoryDmaBuf.VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME,
			EXTImageDrmFormatModifier.VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME,
	};
	
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
	
	/*
	private static void queryDeviceMemTypes(VulkanDevice device) {
		try(MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceMemoryProperties2 memProps = VkPhysicalDeviceMemoryProperties2.calloc(stack).sType$Default();
			VK12.vkGetPhysicalDeviceMemoryProperties2(device.vkDevice().getPhysicalDevice(), memProps);
			for(int i = 0; i < memProps.memoryProperties().memoryTypeCount(); i++) {
				VkMemoryType memType = memProps.memoryProperties().memoryTypes(i);
				System.out.println(String.format("MEMTYPE %d: propertyFlags=0b%s", i, Integer.toBinaryString(memType.propertyFlags())));
			}
		}
	}
	*/
	
	// DMABUF Import process
	// 1. Dmabuf planes with fds into VkDeviceMemory -> VkImportMemoryFdInfoKHR (fd) -> VKMemoryAllocateInfo -> vkAllocateMemory
	// 2. vkCreateImage with ImageCreateInfo -> VkImageDrmFormatModifierExplicitCreateInfoEXT, VkExternalMemoryImageCreateInfo
	// 3. Bind planes to image with vkBindImageMemory2 (VkBindImageMemoryInfo)
	
	public static long importDmabufPlaneToDeviceMemory(VulkanDevice device, Dmabuf dmabuf, int planeIdx) {
		DmabufPlane plane = dmabuf.planes()[planeIdx];
		dmabuf.debugPrint();
		System.out.println("IMPORTING PLANE: " + plane);
		
		try(MemoryStack stack = MemoryStack.stackPush()) {
			int handleType = EXTExternalMemoryDmaBuf.VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT;
			VkMemoryFdPropertiesKHR fdProps = VkMemoryFdPropertiesKHR.calloc(stack).sType$Default();
			KHRExternalMemoryFd.vkGetMemoryFdPropertiesKHR(device.vkDevice(), handleType, plane.fd(), fdProps);
			int memoryTypeBits = fdProps.memoryTypeBits();
			
			int memoryTypeIndex = -1;
			for(int i = 0; i < 32; i++) {
				if((memoryTypeBits & (1 << i)) != 0) {
					memoryTypeIndex = i; // Maybe choose something other than the lowest available memoryType?
					break;
				}
			}
			
			if(memoryTypeIndex < 0) {
				WaylandCraftCommon.LOGGER.error("Error importing dmabuf plane: No memoryTypeIndex allowed! memoryTypeBits: 0b{}", Integer.toBinaryString(memoryTypeBits));
				return MemoryUtil.NULL;
			}
			
			VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack).sType$Default();
			allocateInfo.allocationSize(plane.size());
			allocateInfo.memoryTypeIndex(memoryTypeIndex);
			
			VkImportMemoryFdInfoKHR fdImportInfo = VkImportMemoryFdInfoKHR.calloc(stack).sType$Default();
			fdImportInfo.handleType(handleType);
			fdImportInfo.fd(plane.fd());
			allocateInfo.pNext(fdImportInfo);
			
			LongBuffer deviceMemOut = stack.callocLong(1);
			int result = VK12.vkAllocateMemory(device.vkDevice(), allocateInfo, null, deviceMemOut);
			if(result != VK12.VK_SUCCESS) {
				WaylandCraftCommon.LOGGER.error("Importing dmabuf plane failed! Error: " + result);
				return MemoryUtil.NULL;
			}
			
			long deviceMemory = deviceMemOut.get(0);
			
			System.out.println("Got dmabuf plane VkDeviceMemory: 0x" + Long.toHexString(deviceMemory));
			
			VK12.vkFreeMemory(device.vkDevice(), deviceMemory, null);
			return MemoryUtil.NULL;
		}
	}
	
}
