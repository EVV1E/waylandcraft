package dev.evvie.waylandcraft.vulkanmod.mixin;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Injects VK_KHR_external_memory_fd and VK_EXT_image_drm_format_modifier
 * into VulkanMod's device extension set before the logical device is created,
 * if and only if the physical device supports them.
 *
 * Both extensions are needed for DMA-BUF import:
 *   - VK_KHR_external_memory_fd:       lets us import a DMA-BUF fd as VkDeviceMemory
 *   - VK_EXT_image_drm_format_modifier: lets us create VkImages with a DRM tiling modifier
 *
 * VK_KHR_external_memory is Vulkan 1.1 core (always available, no explicit enablement).
 * VK_EXT_external_memory_dma_buf adds VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT
 * as a valid handle type — this is implicit once VK_KHR_external_memory_fd is enabled.
 */
@Mixin(value = DeviceManager.class, remap = false)
public class VulkanDeviceExtMixin {

    private static final String EXT_DRM_MODIFIER = "VK_EXT_image_drm_format_modifier";
    private static final String EXT_EXT_MEM_FD   = "VK_KHR_external_memory_fd";

    @Inject(method = "createLogicalDevice", at = @At("HEAD"))
    private static void wvc$addDmabufExtensions(CallbackInfo ci) {
        // At this point DeviceManager.device is already selected.
        // Check if both required extensions are available on the physical device.
        Set<String> needed = Set.of(EXT_DRM_MODIFIER, EXT_EXT_MEM_FD);
        Set<String> unsupported = DeviceManager.device.getUnsupportedExtensions(needed);

        if (unsupported.isEmpty()) {
            Vulkan.REQUIRED_EXTENSION.add(EXT_DRM_MODIFIER);
            Vulkan.REQUIRED_EXTENSION.add(EXT_EXT_MEM_FD);
            dev.evvie.waylandcraft.vulkanmod.DmabufImporter.dmabufVulkanAvailable = true;
            WaylandCraftCommon.LOGGER.info("[waylandcraft/vulkanmod] DMA-BUF Vulkan extensions will be enabled: {} {}",
                EXT_DRM_MODIFIER, EXT_EXT_MEM_FD);
        } else {
            WaylandCraftCommon.LOGGER.warn("[waylandcraft/vulkanmod] DMA-BUF Vulkan extensions unavailable on this device: {}",
                unsupported);
            WaylandCraftCommon.LOGGER.warn("[waylandcraft/vulkanmod] DMA-BUF surfaces will show as black (no Vulkan import)");
        }
    }
}
