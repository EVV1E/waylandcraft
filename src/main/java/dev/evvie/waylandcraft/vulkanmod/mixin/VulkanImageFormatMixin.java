package dev.evvie.waylandcraft.vulkanmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * VulkanMod's VulkanImage$Builder.formatSize() switch handles R8G8B8A8 variants
 * but omits VK_FORMAT_B8G8R8A8_UNORM (44).  This causes an IAE whenever
 * ShmUploadMixin passes GL_BGRA as the internalFormat (to avoid the broken
 * BGRAtoRGBA_buffer conversion path), because:
 *
 *   GlUtil.vulkanFormat(GL_BGRA, type) → VK_FORMAT_B8G8R8A8_UNORM (44)
 *   VulkanImage$Builder.formatSize(44) → throw IllegalArgumentException
 *
 * Fix: intercept formatSize at HEAD and return 4 for format 44.
 * B8G8R8A8 is 4 bytes per pixel, identical to the R8G8B8A8 cases already
 * present in the switch.
 */
@Mixin(
    targets = "net.vulkanmod.vulkan.texture.VulkanImage$Builder",
    remap   = false
)
public class VulkanImageFormatMixin {

    private static final int VK_FORMAT_B8G8R8A8_UNORM = 44;

    @Inject(
        method = "formatSize(I)I",
        at     = @At("HEAD"),
        remap  = false,
        cancellable = true
    )
    private static void wvc$formatSizeB8G8R8A8(int format,
                                                CallbackInfoReturnable<Integer> cir) {
        if (format == VK_FORMAT_B8G8R8A8_UNORM) {
            cir.setReturnValue(4);
        }
    }
}
