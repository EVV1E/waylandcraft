package dev.evvie.waylandcraft.compat;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.evvie.waylandcraft.item.WindowItem;

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
			return Items.SPYGLASS;
		}

	}

}
