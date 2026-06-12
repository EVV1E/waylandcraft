package dev.evvie.waylandcraft.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// Used as a proxy interface to client-only by WindowItem
public interface WindowItemInteractionProvider {
	
	// Returns true when an ItemStack still holds a currently alive window
	// The `itemStack` is guaranteed to be an instance of `WindowItem`.
	boolean isValid(ItemStack itemStack);
	
	// Returns printable name for a window item
	// The `itemStack` is guaranteed to be an instance of `WindowItem`.
	@Nullable Component getName(ItemStack itemStack);
	
	// Called every tick while the window item is being used
	// The `itemStack` is guaranteed to be an instance of `WindowItem`.
	void useTick(LivingEntity entity, ItemStack itemStack);
	
}
