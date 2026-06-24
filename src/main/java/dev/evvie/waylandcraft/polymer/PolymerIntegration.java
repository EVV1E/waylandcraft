package dev.evvie.waylandcraft.polymer;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.PolymerWindowItem;
import dev.evvie.waylandcraft.item.WindowItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Every direct reference to a Polymer class in this mod lives here (or in
 * PolymerWindowItem, which this class is the only caller of). Every method
 * here is only ever invoked from a call site already guarded by
 * FabricLoader.isModLoaded("polymer-core") — see WindowItem.register() and
 * WaylandCraftCommon.onInitialize().
 *
 * This split matters because of a real Java constraint: a class can safely
 * CALL another mod's methods conditionally (the JVM only resolves a method
 * call's target class when that specific bytecode instruction actually
 * executes), but it can't safely implement an interface or extend a class
 * from a mod that might not be present — that fails at class-load time,
 * unconditionally, the moment the class is touched at all, regardless of any
 * runtime check wrapped around it. PolymerWindowItem implements PolymerItem,
 * so it (and this class, which is the only thing that constructs it) must
 * only ever be loaded when Polymer is confirmed present.
 */
public class PolymerIntegration {

	public static WindowItem createWindowItem() {
		return new PolymerWindowItem();
	}

	/**
	 * Excludes both registry entries from Fabric API's hard registry-sync
	 * requirement. Without this, every connecting client — Polymer-aware or
	 * not — would still be forced to acknowledge them, defeating the whole
	 * point of the Polymer integration.
	 */
	public static void excludeFromRegistrySync() {
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.ITEM, WindowItem.WINDOW);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.DATA_COMPONENT_TYPE, WindowItem.WINDOW_HANDLE);
	}

	/**
	 * Feeds this mod's own assets (textures/models, including the window
	 * item's texture and plain static model) into Polymer's single combined
	 * resource pack, which gets pushed to every connecting player regardless
	 * of whether they have WaylandCraft. This is what lets
	 * PolymerWindowItem.getPolymerItemModel() give non-aware players a
	 * proper window icon instead of plain paper.
	 */
	public static void registerResourcePackAssets() {
		PolymerResourcePackUtils.addModAssets(WaylandCraftCommon.MOD_ID);
	}

}
