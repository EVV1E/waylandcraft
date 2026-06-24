package dev.evvie.waylandcraft.vulkanmod.mixin;

import net.vulkanmod.vulkan.memory.MemoryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * VulkanImage has two constructors:
 *
 *   (1) VulkanImage.Builder path — allocates a VkImage + VMA memory block,
 *       stores the allocation handle in the private {@code allocation} field.
 *
 *   (2) 9-arg public constructor ("Used for already allocated images e.g.
 *       swap chain images") — sets raw Vulkan handles only; {@code allocation}
 *       stays 0L because no VMA allocation was made.
 *
 * DmabufImporter uses path (2) to wrap imported DMA-BUF memory as VulkanImages
 * and registers them with VulkanMod via VkGlTexture.bindIdToImage().
 *
 * When WaylandCraft replaces or closes a surface, VulkanMod schedules the old
 * VulkanImage for deferred deletion.  At the next frame boundary:
 *   MemoryManager.initFrame → freeImages → VulkanImage.doFree →
 *   MemoryManager.freeImage(this.id, this.allocation /*=0*&#47;)
 *   → images.remove(this.id) returns null (externals are not tracked)
 *   → NPE on image.size
 *
 * Fix: redirect the MemoryManager.freeImage call inside doFree().  If
 * {@code allocation == 0}, the image is externally managed and not tracked in
 * the VMA map — silently skip the free.  Otherwise forward to the real method.
 *
 * Note: we do NOT free the underlying VkImage/VkImageView here.  For
 * DMA-BUF-imported images those are cleaned up by the kernel when the fd is
 * closed (implicit lifetime via DRM GEM reference counting).  A proper
 * explicit-cleanup path can be added later; for now the skip is safe.
 */
@Mixin(
    targets = "net.vulkanmod.vulkan.texture.VulkanImage",
    remap   = false
)
public class VulkanImageMixin {

    @Redirect(
        method  = "doFree",
        at      = @At(
            value  = "INVOKE",
            target = "Lnet/vulkanmod/vulkan/memory/MemoryManager;freeImage(JJ)V",
            remap  = false
        ),
        require = 1
    )
    private void wvc$guardFreeImage(long imageId, long allocation) {
        if (allocation == 0L) {
            // External / imported image — no VMA allocation, not in the
            // MemoryManager tracking map.  Skipping avoids the NPE in
            // MemoryManager.freeImage where images.remove(imageId) returns null.
            return;
        }
        MemoryManager.freeImage(imageId, allocation);
    }
}
