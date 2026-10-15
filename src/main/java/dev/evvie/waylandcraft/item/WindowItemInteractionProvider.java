package dev.evvie.waylandcraft.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// Used as a proxy interface to client-only by WindowItem
public interface WindowItemInteractionProvider {
	
	// Returns true when an ItemStack still holds a currently alive window
	// Caller guantees itemStack is an instance of WindowItem
	boolean isValid(ItemStack itemStack);
	
	// Returns printable name for a window item
	// Caller guantees itemStack is an instance of WindowItem
	@Nullable Component getName(ItemStack itemStack);
	
}
