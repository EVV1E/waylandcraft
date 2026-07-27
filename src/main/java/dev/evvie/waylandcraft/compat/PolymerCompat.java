package dev.evvie.waylandcraft.compat;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
	}

	private static class WindowItemPolymerOverlay implements PolymerItem {

		@Override
		public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
			// Only virtualize for clients that don't actually have
			// WaylandCraft -- players who do (detected via the mod's own
			// networking channel being registered on their connection)
			// should see and interact with the real item, not the fallback.
			ServerPlayer player = PolymerCommonUtils.getPlayer(context);
			if(player != null && ServerPlayNetworking.canSend(player, ServerboundGiveItemsPayload.TYPE)) {
				return WindowItem.WINDOW;
			}

			return Items.PAPER;
		}

	}

}
