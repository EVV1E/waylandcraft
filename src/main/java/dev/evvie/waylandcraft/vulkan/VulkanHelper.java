package dev.evvie.waylandcraft.vulkan;

import java.nio.LongBuffer;
import java.util.ArrayList;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTExternalMemoryDmaBuf;
import org.lwjgl.vulkan.EXTImageDrmFormatModifier;
import org.lwjgl.vulkan.KHRExternalMemoryFd;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBindImageMemoryInfo;
import org.lwjgl.vulkan.VkDrmFormatModifierPropertiesEXT;
import org.lwjgl.vulkan.VkDrmFormatModifierPropertiesListEXT;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkFormatProperties2;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageDrmFormatModifierExplicitCreateInfoEXT;
import org.lwjgl.vulkan.VkImportMemoryFdInfoKHR;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryFdPropertiesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceDrmPropertiesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.lwjgl.vulkan.VkSubresourceLayout;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufPlane;

public class VulkanHelper {
	
	/* See VulkanGpuTextureMixin, VulkanGpuTextureViewMixin and BufferTexture */
	public static final ScopedValue<Integer> VULKAN_GPU_TEXTURE_CREATE_FORMAT_OVERRIDE = ScopedValue.newInstance();
	public static final ScopedValue<Long> VULKAN_GPU_TEXTURE_IMAGE_CREATE_OVERRIDE = ScopedValue.newInstance();
	
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
	
	public static record DrmModifiersInfo(long modifier, int numPlanes, int tilingFeatures) {}
	
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
				mods.add(new DrmModifiersInfo(modifier.drmFormatModifier(), modifier.drmFormatModifierPlaneCount(), modifier.drmFormatModifierTilingFeatures()));
			}
		}
		return mods;
	}
	
	public static record ImportedDmabufVulkan(long vkImage, long[] planeDeviceMemory, int vkFormat) {}
	
	private static long importDmabufPlaneToDeviceMemory(VulkanDevice device, Dmabuf dmabuf, int planeIdx) {
		DmabufPlane plane = dmabuf.planes()[planeIdx];
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
			return deviceMemory;
		}
	}
	
	public static void destroyImportedDmabuf(VulkanDevice device, ImportedDmabufVulkan importedDmabuf) {
		for(int i = 0; i < importedDmabuf.planeDeviceMemory.length; i++) {
			long deviceMemory = importedDmabuf.planeDeviceMemory[i];
			if(deviceMemory == MemoryUtil.NULL) continue; // For failed imports the VkDeviceMemory may be NULL
			VK12.vkFreeMemory(device.vkDevice(), deviceMemory, null);
		}
		VK12.vkDestroyImage(device.vkDevice(), importedDmabuf.vkImage, null);
	}
	
	public static ImportedDmabufVulkan importDmabuf(VulkanDevice device, Dmabuf dmabuf) {
		dmabuf.debugPrint();
		
		int vkFormat = -1;
		long modifier = dmabuf.modifier();
		for(DrmVulkanFormat format : DrmVulkanFormat.FORMATS) {
			if(format.fourcc() == dmabuf.format()) {
				vkFormat = format.vkFormat();
				break;
			}
		}
		
		if(vkFormat < 0) {
			WaylandCraftCommon.LOGGER.error(String.format("importDmabuf: Unknown dmabuf format 0x%016X", dmabuf.format()));
			return null;
		}
		
		int numPlanes = dmabuf.planes().length;
		
		long vkImage;
		try(MemoryStack stack = MemoryStack.stackPush()) {
			VkImageDrmFormatModifierExplicitCreateInfoEXT modifierExplicitImageCreateInfo = VkImageDrmFormatModifierExplicitCreateInfoEXT.calloc(stack).sType$Default();
			modifierExplicitImageCreateInfo.drmFormatModifier(modifier);
			MemoryUtil.memPutInt(modifierExplicitImageCreateInfo.address() + VkImageDrmFormatModifierExplicitCreateInfoEXT.DRMFORMATMODIFIERPLANECOUNT, numPlanes);
			VkSubresourceLayout.Buffer planeLayouts = VkSubresourceLayout.calloc(numPlanes, stack);
			for(int i = 0; i < numPlanes; i++) {
				DmabufPlane plane = dmabuf.planes()[i];
				VkSubresourceLayout planeLayout = planeLayouts.get(i);
				planeLayout.offset(plane.offset());
				planeLayout.rowPitch(plane.stride());
				planeLayout.arrayPitch(0); // arrayLayers = 1
			}
			modifierExplicitImageCreateInfo.pPlaneLayouts(planeLayouts);
			
			VkExternalMemoryImageCreateInfo externalMemoryImageCreateInfo = VkExternalMemoryImageCreateInfo.calloc(stack).sType$Default();
			externalMemoryImageCreateInfo.handleTypes(EXTExternalMemoryDmaBuf.VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT);
			
			VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack).sType$Default();
			imageCreateInfo.flags(VK12.VK_IMAGE_CREATE_ALIAS_BIT);
			imageCreateInfo.imageType(VK12.VK_IMAGE_TYPE_2D);
			imageCreateInfo.format(vkFormat);
			imageCreateInfo.extent().set(dmabuf.width(), dmabuf.height(), 1);
			imageCreateInfo.mipLevels(1);
			imageCreateInfo.arrayLayers(1);
			imageCreateInfo.samples(VK12.VK_SAMPLE_COUNT_1_BIT);
			imageCreateInfo.tiling(EXTImageDrmFormatModifier.VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT);
			imageCreateInfo.usage(VK12.VK_IMAGE_USAGE_SAMPLED_BIT);
			imageCreateInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
			imageCreateInfo.initialLayout(VK12.VK_IMAGE_LAYOUT_UNDEFINED);
			
			imageCreateInfo.pNext(externalMemoryImageCreateInfo.address());
			externalMemoryImageCreateInfo.pNext(modifierExplicitImageCreateInfo.address());
			
			LongBuffer imageRet = stack.callocLong(1);
			int result = VK12.vkCreateImage(device.vkDevice(), imageCreateInfo, null, imageRet);
			
			if(result != VK12.VK_SUCCESS) {
				WaylandCraftCommon.LOGGER.error("vkCreateImage failed! Error: " + result);
				return null;
			}
			
			vkImage = imageRet.get(0);
		}
		
		System.out.println(String.format("Got VkImage: 0x%016X", vkImage));
		
		boolean planeFailed = false;
		long[] planesDeviceMem = new long[numPlanes];
		for(int i = 0; i < numPlanes; i++) {
			long deviceMemory = importDmabufPlaneToDeviceMemory(device, dmabuf, i);
			if(deviceMemory == MemoryUtil.NULL) {
				planeFailed = true;
				break;
			}
			
			planesDeviceMem[i] = deviceMemory;
		}
		
		ImportedDmabufVulkan importedDmabuf = new ImportedDmabufVulkan(vkImage, planesDeviceMem, vkFormat);
		
		if(planeFailed) {
			// If one of the planes failed, destroy the whole thing now
			destroyImportedDmabuf(device, importedDmabuf);
			return null;
		}
		
		try(MemoryStack stack = MemoryStack.stackPush()) {
			VkBindImageMemoryInfo.Buffer bindInfos = VkBindImageMemoryInfo.calloc(numPlanes, stack);
			for(int i = 0; i < numPlanes; i++) {
				VkBindImageMemoryInfo info = bindInfos.get(i);
				info.sType$Default();
				info.image(vkImage);
				info.memory(planesDeviceMem[i]);
				info.memoryOffset(0);
			}
			
			int result = VK12.vkBindImageMemory2(device.vkDevice(), bindInfos);
			if(result != VK12.VK_SUCCESS) {
				WaylandCraftCommon.LOGGER.error("vkBindImageMemory2 failed! Error: " + result);
				destroyImportedDmabuf(device, importedDmabuf);
				return null;
			}
		}
		
		System.out.println("Dmabuf import successful!");
		
		return importedDmabuf;
	}
	
}
