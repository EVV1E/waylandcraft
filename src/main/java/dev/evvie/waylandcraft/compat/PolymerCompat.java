package dev.evvie.waylandcraft.compat;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.network.ServerboundGiveItemsPayload;

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

	public static void register() {
		PolymerItem.registerOverlay(WindowItem.WINDOW, new WindowItemPolymerOverlay());

		// WINDOW_HANDLE is a separate registry entry (DataComponentType,
		// not Item) and isn't covered by registerOverlay above -- without
		// this, Fabric's registry sync requires every connecting client to
		// know about it too, same as the item would without the overlay.
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.DATA_COMPONENT_TYPE, WindowItem.WINDOW_HANDLE);
	}

	private static class WindowItemPolymerOverlay implements PolymerItem {

		@Override
		public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
			if(hasWaylandCraft(context)) return WindowItem.WINDOW;
			return Items.PAPER;
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
			boolean result = player != null && ServerPlayNetworking.canSend(player, ServerboundGiveItemsPayload.TYPE);
			WaylandCraftCommon.LOGGER.info("PolymerCompat.hasWaylandCraft: player={} result={}", player == null ? "null" : player.getGameProfile().getName(), result);
			return result;
		}

	}

}
