package dev.evvie.waylandcraft.vulkanmod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Single source of truth for "is VulkanMod present" — checked from core
 * WaylandCraft classes (BufferTexture, WindowFramebuffer, etc.) that must
 * load and run identically whether VulkanMod is installed or not.
 *
 * IMPORTANT: this class must never import anything from net.vulkanmod.*.
 * FabricLoader is always present (it's the mod loader itself), so reading
 * {@link #ACTIVE} never risks a NoClassDefFoundError. The actual VulkanMod
 * interop logic lives in {@link VulkanModInterop}, which DOES import
 * net.vulkanmod.* classes — that class must only ever be touched from
 * inside an `if (WaylandCraftVulkanSupport.ACTIVE)` branch, so its
 * VulkanMod-referencing bytecode is never linked/executed on a vanilla-GL
 * setup.
 */
public final class WaylandCraftVulkanSupport {

    public static final boolean ACTIVE =
            FabricLoader.getInstance().isModLoaded("vulkanmod");

    private WaylandCraftVulkanSupport() {}
}
