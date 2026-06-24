package dev.evvie.waylandcraft.polymer.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Gates the entire waylandcraft.polymer.mixins.json config on whether
 * Polymer is installed.
 *
 * Unlike the (removed) VulkanMod mixin plugin this mirrors, the two mixins
 * here — LivingEntityEquipmentMixin, ServerEntityEquipmentMixin — target
 * plain vanilla classes (LivingEntity, ServerEntity) that always exist, not
 * some other mod's internals. The problem isn't a missing target class —
 * it's that their METHOD BODIES call Polymer APIs (PolymerItemUtils,
 * PolymerEntity, PacketContext) directly. Mixin weaves those bodies
 * straight into vanilla's own equipment-broadcast methods, which run
 * constantly for every living entity, not just ones holding a polymer item.
 * If Polymer isn't present, the very first equipped-entity update in any
 * world would try to resolve a Polymer class that doesn't exist and crash —
 * regardless of any FabricLoader.isModLoaded() check written inside the
 * mixin body itself, since by then the bytecode referencing it is
 * unconditionally part of the target method.
 *
 * shouldApplyMixin() is consulted before each mixin is actually WOVEN into
 * its target — returning false here skips weaving entirely when Polymer is
 * absent, so the target methods are left exactly as vanilla, and the
 * Polymer-referencing bytecode never becomes part of them in the first
 * place.
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
