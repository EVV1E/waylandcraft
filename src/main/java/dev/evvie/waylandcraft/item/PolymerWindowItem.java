package dev.evvie.waylandcraft.item;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.network.ClientboundPresenceMarkerPayload;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Polymer-aware variant of WindowItem, used in place of the plain base
 * class whenever Polymer is actually present (see WindowItem.register() and
 * PolymerIntegration). Only ever constructed via
 * PolymerIntegration.createWindowItem() — never reference this class from
 * anywhere that isn't already behind a FabricLoader.isModLoaded("polymer-core")
 * check, or it'll be loaded (and fail, since PolymerItem won't exist)
 * regardless of any such check elsewhere.
 *
 * WITHOUT this (i.e. Polymer absent, plain WindowItem registered instead):
 * every connecting client (including vanilla, including Fabric clients that
 * simply don't have this mod) gets disconnected, because Fabric API's
 * registry-sync handshake requires every client to acknowledge every
 * non-vanilla registry entry — including this one. That's the cost of
 * running without Polymer: it isn't a missing nicety, it's the difference
 * between "non-WaylandCraft players can join at all" and "they can't."
 *
 * WITH Polymer: PolymerIntegration.excludeFromRegistrySync() (called from
 * WindowItem.register() when this class is in use) flags both this Item and
 * the WINDOW_HANDLE DataComponentType as "server-only" entries, so that
 * handshake never has to happen at all.
 *
 * IMPORTANT, and easy to get wrong: Polymer's getPolymerItemStack()/
 * createItemStack() pipeline runs UNCONDITIONALLY for every connecting
 * player, including the player's own client — there is no built-in "skip
 * this for Polymer-aware clients" branch anywhere in Polymer itself.
 * Polymer's own handshake only proves a player has Polymer installed, not
 * that they have THIS mod. So getPolymerItem() below does its own per-player
 * check via ServerPlayNetworking.canSend() against
 * ClientboundPresenceMarkerPayload — a channel only WaylandCraft-having
 * clients register — and returns the real item (itself) only for those
 * players. Everyone else gets the inert Items.PAPER substitute.
 *
 * Returning the real item isn't enough on its own, though: createItemStack()
 * only copies a hardcoded list of vanilla DataComponentTypes onto the
 * rebuilt stack, which doesn't include our custom WINDOW_HANDLE. Without
 * restoring it explicitly (see modifyBasePolymerItemStack below), even a
 * WaylandCraft-having player would receive a correctly-typed but functionally
 * inert item — handle-less, so WindowItemInteractionProvider has nothing to
 * key off of.
 */
public class PolymerWindowItem extends WindowItem implements PolymerItem {

	/**
	 * Per-player: returns the real item for connections that have
	 * WaylandCraft installed (detected via canSend, see class doc), and the
	 * inert Items.PAPER substitute for everyone else.
	 */
	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		ServerPlayer player = PolymerCommonUtils.getPlayer(context);
		if (player != null && ServerPlayNetworking.canSend(player, ClientboundPresenceMarkerPayload.TYPE)) {
			return WINDOW;
		}
		return Items.PAPER;
	}

	/**
	 * Gives the inert Items.PAPER substitute a real icon instead of plain
	 * paper, for non-aware players only. Returning a model here, for an
	 * aware player, would override the real WINDOW item's own
	 * select-property model (resolved via its registry-name-keyed item
	 * definition file, items/window.json) with this plain one — so this
	 * must stay gated exactly like getPolymerItem() above, returning null
	 * (meaning "use whatever the substituted item already resolves to on
	 * its own") for aware viewers.
	 *
	 * Points at "window_polymer_fallback", a SEPARATE plain item
	 * definition (items/window_polymer_fallback.json — just a bare
	 * minecraft:model wrapper around models/item/window.json), NOT at
	 * "window" itself. DataComponents.ITEM_MODEL resolves its identifier
	 * against items/<path>.json — and items/window.json is the real
	 * item's complex select-property dispatcher (keyed on the custom
	 * waylandcraft:window_state property), which only resolves for
	 * WaylandCraft-aware clients; their SelectItemModelProperties.ID_MAPPER
	 * registration for that property only happens in the mod's own
	 * client-side init. A non-aware client has no handler for it at all,
	 * so the whole dispatcher fails and falls back to the missing-model
	 * checkerboard — even with every individual asset file correctly
	 * present in the resource pack, as confirmed by direct inspection.
	 */
	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		ServerPlayer player = PolymerCommonUtils.getPlayer(context);
		if (player != null && ServerPlayNetworking.canSend(player, ClientboundPresenceMarkerPayload.TYPE)) {
			return null;
		}
		return Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_polymer_fallback");
	}

	/**
	 * createItemStack() only copies a fixed list of vanilla components onto
	 * the rebuilt stack — WINDOW_HANDLE isn't on it, so it has to be restored
	 * by hand here, but ONLY for players who got the real item back from
	 * getPolymerItem() above. A non-WaylandCraft client doesn't have
	 * WINDOW_HANDLE registered at all; attaching it to their stack — even
	 * one whose base item is the inert Items.PAPER substitute — makes the
	 * whole packet undecodable on their end and disconnects them. The base
	 * item being safe for them doesn't make an unknown component safe too;
	 * those are decoded independently.
	 */
	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		ServerPlayer player = PolymerCommonUtils.getPlayer(context);
		if (player == null || !ServerPlayNetworking.canSend(player, ClientboundPresenceMarkerPayload.TYPE)) {
			return;
		}
		Long handle = stack.get(WINDOW_HANDLE);
		if (handle != null) {
			out.set(WINDOW_HANDLE, handle);
		}
	}

}
