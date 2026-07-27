package dev.evvie.waylandcraft.compat;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.network.ClientboundHelloPayload;

/**
 * Registers WaylandCraft's items as Polymer overlays, using
 * {@link PolymerItem#registerOverlay} rather than implementing PolymerItem
 * directly on the item classes -- this keeps WindowItem free of any
 * compile-time or class-load-time dependency on Polymer, so the mod works
 * fine with Polymer absent.
 *
 * This class itself must never be referenced unless polymer-core is
 * confirmed loaded -- see the call site in WaylandCraftCommon.
 */
public class PolymerCompat {

	// The model shown to non-WaylandCraft clients: a plain generated model
	// (assets/waylandcraft/models/item/window.json) using the static window
	// texture, as opposed to the real item's model, which selects between a
	// custom special renderer/broken-window state that only our own client
	// code can evaluate.
	private static final Identifier FALLBACK_MODEL = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "item/window");

	public static void register() {
		// Makes our own textures/models (including FALLBACK_MODEL above)
		// available in Polymer's generated resource pack, so non-modded
		// clients actually have something to render for the fallback model
		// rather than a missing-texture placeholder.
		if(FabricLoader.getInstance().isModLoaded("polymer-resource-pack")) {
			ResourcePackHook.addAssets();
		}

		PolymerItem.registerOverlay(WindowItem.WINDOW, new WindowItemPolymerOverlay());

		// WINDOW_HANDLE is a separate registry entry (DataComponentType,
		// not Item) and isn't covered by registerOverlay above -- without
		// this, Fabric's registry sync requires every connecting client to
		// know about it too, same as the item would without the overlay.
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.DATA_COMPONENT_TYPE, WindowItem.WINDOW_HANDLE);
	}

	// Isolated in its own class so merely loading PolymerCompat doesn't
	// force-load PolymerResourcePackUtils (from the separate
	// polymer-resource-pack module) -- see the isModLoaded guard at the
	// call site in WaylandCraftCommon for why this pattern matters.
	private static class ResourcePackHook {

		private static void addAssets() {
			eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.addModAssets(WaylandCraftCommon.MOD_ID);
		}

	}

	private static class WindowItemPolymerOverlay implements PolymerItem {

		@Override
		public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
			if(hasWaylandCraft(context)) return WindowItem.WINDOW;
			return Items.PAPER;
		}

		@Override
		public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
			if(hasWaylandCraft(context)) return PolymerItem.super.getPolymerItemModel(stack, context, lookup);
			return FALLBACK_MODEL;
		}

		@Override
		public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
			// Real WaylandCraft clients need WINDOW_HANDLE to identify which
			// toplevel the item refers to; other clients don't know the
			// component exists and don't need the data either.
			if(!hasWaylandCraft(context)) out.remove(WindowItem.WINDOW_HANDLE);
		}

		@Override
		public ItemStack getPolymerItemStack(ItemStack itemStack, TooltipFlag tooltipType, PacketContext context, HolderLookup.Provider lookup) {
			ItemStack out = PolymerItem.super.getPolymerItemStack(itemStack, tooltipType, context, lookup);
			if(hasWaylandCraft(context) && itemStack.has(WindowItem.WINDOW_HANDLE)) {
				out.set(WindowItem.WINDOW_HANDLE, itemStack.get(WindowItem.WINDOW_HANDLE));
			}
			return out;
		}

		// Detects whether the connecting player has WaylandCraft installed,
		// via the mod's own networking channel being registered on their
		// connection -- players who do should see and interact with the
		// real item/data, not the vanilla-safe fallback.
		private static boolean hasWaylandCraft(PacketContext context) {
			ServerPlayer player = PolymerCommonUtils.getPlayer(context);
			boolean result = player != null && ServerPlayNetworking.canSend(player, ClientboundHelloPayload.TYPE);
			WaylandCraftCommon.LOGGER.info("PolymerCompat.hasWaylandCraft: player={} result={}", player == null ? "null" : player.getGameProfile().name(), result);
			return result;
		}

	}

}
