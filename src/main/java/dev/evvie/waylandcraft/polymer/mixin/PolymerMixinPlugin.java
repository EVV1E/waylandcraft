package dev.evvie.waylandcraft.polymer.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Gates the entire waylandcraft.polymer.mixins.json config on whether
 * Polymer is installed.  The mixin list is currently empty; this plugin
 * remains so that any future Polymer-API-calling mixins can be added here
 * and will be safely skipped when Polymer is absent.
 */
public class PolymerMixinPlugin implements IMixinConfigPlugin {

	private boolean active;

	@Override
	public void onLoad(String mixinPackage) {
		active = FabricLoader.getInstance().isModLoaded("polymer-core");
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
