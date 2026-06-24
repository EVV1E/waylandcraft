package dev.evvie.waylandcraft.vulkanmod.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Gates the entire waylandcraft.vulkanmod.mixins.json config on whether
 * VulkanMod is installed.
 *
 * The four mixins in this config (VulkanDeviceExtMixin, VulkanImageFormatMixin,
 * VulkanImageMixin, VkGpuBufferMixin) target VulkanMod's own internal classes
 * by name. If VulkanMod isn't loaded, those target classes don't exist on the
 * classpath at all — applying the mixin config unconditionally would throw a
 * ClassNotFoundException during mixin application and crash the game on
 * startup, even for players using vanilla GL.
 *
 * shouldApplyMixin() is consulted by Mixin before each individual mixin is
 * applied, so returning false here for everything skips application entirely
 * (and skips the target-class lookup) when VulkanMod is absent.
 */
public class WaylandCraftVulkanModMixinPlugin implements IMixinConfigPlugin {

	private boolean active;

	@Override
	public void onLoad(String mixinPackage) {
		active = FabricLoader.getInstance().isModLoaded("vulkanmod");
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return active;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
