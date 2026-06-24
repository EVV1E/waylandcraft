package dev.evvie.waylandcraft.vulkanmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * VkGpuBuffer.<init> calls supplier.get() unconditionally despite the parameter
 * being annotated @Nullable:
 *
 *   this.buffer = new Buffer(supplier.get(), vkUsage, memoryType);  // line 54
 *
 * GpuDevice.createBuffer() accepts a @Nullable Supplier<String> for a debug
 * label.  WaylandCraft's WindowFramebuffer.BufferDraw.compile() passes null,
 * which is perfectly legal — but VulkanMod doesn't guard against it, causing
 * a NullPointerException the first time a Wayland window tries to render.
 *
 * We redirect the Supplier.get() call and return a fallback label when the
 * supplier itself is null.
 */
@Mixin(
    targets = "net.vulkanmod.render.engine.VkGpuBuffer",
    remap   = false
)
public class VkGpuBufferMixin {

    @Redirect(
        method  = "<init>",
        at      = @At(
            value  = "INVOKE",
            target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;",
            remap  = false
        ),
        require = 1
    )
    private Object wvc$safeSupplierGet(Supplier<?> supplier) {
        return supplier != null ? supplier.get() : "waylandcraft-buffer";
    }
}
