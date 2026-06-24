package dev.evvie.waylandcraft.vulkanmod;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Imports a Wayland DMA-BUF surface (delivered via WaylandCraft as an EGLImage)
 * into VulkanMod's texture system via native Vulkan DMA-BUF import.
 *
 * The pipeline is:
 *   1. EGL exports the DMA-BUF fd, fourcc, modifier, stride and offset via
 *      EGL_MESA_image_dma_buf_export (see EglHelper.exportDmabuf).
 *   2. We create a VkImage with VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT and
 *      the explicit modifier from step 1.
 *   3. We import the fd as VkDeviceMemory via VkImportMemoryFdInfoKHR; Vulkan
 *      takes ownership of the fd on success.
 *   4. We wrap the VkImage in VulkanMod's VulkanImage (allocation=0 to signal
 *      "externally managed") and register it with VkGlTexture.bindIdToImage().
 *
 * The VkImage is a zero-copy live view into the DMA-BUF: the GPU compositor
 * writes directly into the buffer; no CPU readback or re-import is needed on
 * subsequent frames.
 *
 * Cleanup is handled by VulkanImageMixin: when VulkanMod schedules our
 * VulkanImage for deferred deletion it calls freeDmabufResources(), which
 * destroys the VkImage and VkDeviceMemory we allocated here.
 */
public final class DmabufImporter {

    /** Set by VulkanDeviceExtMixin once both required extensions are enabled. */
    public static volatile boolean dmabufVulkanAvailable = false;

    /** VkImage handle → VkDeviceMemory handle for every imported DMA-BUF image. */
    private static final ConcurrentHashMap<Long, Long> importedMemory = new ConcurrentHashMap<>();

    // ── Vulkan extension constants ─────────────────────────────────────────────
    private static final int VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT = 1000158000;
    private static final int VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO = 1000072001;
    private static final int VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT = 1000158004;
    private static final int VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR = 1000074000;
    private static final int VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT = 0x200;

    // ── DRM fourcc codes (little-endian) ──────────────────────────────────────
    private static final int DRM_FORMAT_ARGB8888 = 0x34325241;
    private static final int DRM_FORMAT_XRGB8888 = 0x34325258;

    // ── VK_FORMAT_B8G8R8A8_UNORM ──────────────────────────────────────────────
    private static final int VK_FORMAT_B8G8R8A8_UNORM = 44;

    // ── libc close() for fd cleanup on import failure ─────────────────────────
    private static final long FN_CLOSE;
    static {
        long fn = 0L;
        try {
            SharedLibrary libc = Library.loadNative(DmabufImporter.class, null, "libc.so.6");
            if (libc != null) fn = libc.getFunctionAddress("close");
        } catch (Exception ignored) {}
        FN_CLOSE = fn;
    }

    private DmabufImporter() {}

    private static void closeFd(int fd) {
        if (FN_CLOSE != 0L && fd >= 0) JNI.invokeI(fd, FN_CLOSE);
    }

    private static int fourccToVkFormat(int fourcc) {
        return switch (fourcc) {
            case DRM_FORMAT_ARGB8888, DRM_FORMAT_XRGB8888 -> VK_FORMAT_B8G8R8A8_UNORM;
            default -> -1;
        };
    }

    /**
     * Import a DMA-BUF backed EGLImage as a VulkanImage and register it with
     * VulkanMod's texture system under the given GL texture id.
     *
     * <p>On success, the VkImage is a live zero-copy view of the DMA-BUF;
     * subsequent frames need no re-import.  Returns false on any failure,
     * ensuring the caller has a valid backing image before it attempts to draw.
     */
    public static boolean importDmabuf(int texId, long display, long eglImage, int width, int height) {
        if (!dmabufVulkanAvailable) return false;
        if (display == 0L || eglImage == 0L) return false;
        if (width <= 0 || height <= 0) return false;

        EglHelper.DmabufExportInfo info = EglHelper.exportDmabuf(display, eglImage);
        if (info == null) return false;

        int vkFormat = fourccToVkFormat(info.fourcc());
        if (vkFormat < 0) {
            WaylandCraftCommon.LOGGER.warn("[waylandcraft/vulkanmod] Unknown DRM fourcc 0x{} — skipping Vulkan import",
                Integer.toHexString(info.fourcc()));
            closeFd(info.fd());
            return false;
        }

        VkDevice device = Vulkan.getVkDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Plane 0 layout as reported by EGL export.
            // size is ignored by the driver for explicit DRM modifier images.
            VkSubresourceLayout.Buffer planeLayouts = VkSubresourceLayout.calloc(1, stack);
            planeLayouts.get(0)
                .offset(info.offset())
                .rowPitch(info.stride())
                .size(0)
                .arrayPitch(0)
                .depthPitch(0);

            // pNext chain:
            //   VkImageCreateInfo
            //     → VkExternalMemoryImageCreateInfo
            //         → VkImageDrmFormatModifierExplicitCreateInfoEXT
            VkImageDrmFormatModifierExplicitCreateInfoEXT drmModifierInfo =
                VkImageDrmFormatModifierExplicitCreateInfoEXT.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT)
                    .drmFormatModifier(info.modifier())
                    .drmFormatModifierPlaneCount(1)
                    .pPlaneLayouts(planeLayouts);

            VkExternalMemoryImageCreateInfo extMemImageInfo = VkExternalMemoryImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO)
                .pNext(drmModifierInfo.address())
                .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT);

            VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .pNext(extMemImageInfo.address())
                .imageType(VK_IMAGE_TYPE_2D)
                .format(vkFormat)
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT)
                .usage(VK_IMAGE_USAGE_SAMPLED_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageCreateInfo.extent().width(width).height(height).depth(1);

            LongBuffer pImage = stack.mallocLong(1);
            int result = vkCreateImage(device, imageCreateInfo, null, pImage);
            if (result != VK_SUCCESS) {
                WaylandCraftCommon.LOGGER.error("[waylandcraft/vulkanmod] vkCreateImage failed: {}", result);
                closeFd(info.fd());
                return false;
            }
            long vkImage = pImage.get(0);

            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, vkImage, memReqs);

            // VK_KHR_external_memory_fd: import the DMA-BUF fd as VkDeviceMemory.
            // Vulkan takes ownership of the fd on success; close it ourselves on failure.
            VkImportMemoryFdInfoKHR importFdInfo = VkImportMemoryFdInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR)
                .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT)
                .fd(info.fd());

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .pNext(importFdInfo.address())
                .allocationSize(memReqs.size())
                .memoryTypeIndex(Integer.numberOfTrailingZeros(memReqs.memoryTypeBits()));

            LongBuffer pMemory = stack.mallocLong(1);
            result = vkAllocateMemory(device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                WaylandCraftCommon.LOGGER.error("[waylandcraft/vulkanmod] vkAllocateMemory (DMA-BUF import) failed: {}", result);
                closeFd(info.fd());
                vkDestroyImage(device, vkImage, null);
                return false;
            }
            long vkMemory = pMemory.get(0);

            result = vkBindImageMemory(device, vkImage, vkMemory, 0);
            if (result != VK_SUCCESS) {
                WaylandCraftCommon.LOGGER.error("[waylandcraft/vulkanmod] vkBindImageMemory failed: {}", result);
                vkFreeMemory(device, vkMemory, null);
                vkDestroyImage(device, vkImage, null);
                return false;
            }

            long imageView = VulkanImage.createImageView(vkImage, vkFormat, VK_IMAGE_ASPECT_COLOR_BIT, 1, 1);
            if (imageView == 0L) {
                WaylandCraftCommon.LOGGER.error("[waylandcraft/vulkanmod] VulkanImage.createImageView failed");
                vkFreeMemory(device, vkMemory, null);
                vkDestroyImage(device, vkImage, null);
                return false;
            }

            // Wrap in VulkanImage — allocation stays 0L (externally managed; no VMA).
            // VulkanImageMixin guards freeImage() to skip VMA for these.
            VulkanImage vulkanImage = new VulkanImage(
                "dmabuf-" + Long.toHexString(vkImage),
                vkImage, vkFormat, 1, width, height, 4,
                VK_IMAGE_USAGE_SAMPLED_BIT, imageView
            );

            importedMemory.put(vkImage, vkMemory);

            VkGlTexture.bindIdToImage(texId, vulkanImage);
            vulkanImage.readOnlyLayout();

            WaylandCraftCommon.LOGGER.debug("[waylandcraft/vulkanmod] DMA-BUF imported: {}×{} fourcc=0x{} mod=0x{} VkImage=0x{}",
                width, height,
                Integer.toHexString(info.fourcc()),
                Long.toHexString(info.modifier()),
                Long.toHexString(vkImage));
            return true;
        }
    }

    /**
     * Called from VulkanImageMixin when VulkanMod defers deletion of an
     * externally managed image (allocation == 0).  Destroys the VkImage and
     * VkDeviceMemory we allocated during importDmabuf().
     *
     * If imageId is not in importedMemory this is a no-op, so it is safe to
     * call for any VulkanImage whose allocation is 0 (e.g. swap chain images).
     */
    public static void freeDmabufResources(long imageId) {
        Long memory = importedMemory.remove(imageId);
        if (memory == null) return;
        VkDevice device = Vulkan.getVkDevice();
        vkDestroyImage(device, imageId, null);
        vkFreeMemory(device, memory, null);
    }
}
