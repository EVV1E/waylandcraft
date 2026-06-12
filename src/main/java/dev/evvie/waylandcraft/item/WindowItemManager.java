package dev.evvie.waylandcraft.item;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class WindowItemManager implements WindowItemInteractionProvider {
	
	private static Component UNKNOWN_WINDOW_TEXT = Component.literal("Unknown Window");
	
	public void giveItem(WLCToplevel toplevel) {
		/* TODO */
	}
	
	public void giveItemsIfMissing(WLCToplevel... toplevels) {
		/* TODO */
	}
	
	@Override
	public boolean isValid(ItemStack itemStack) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(itemStack);
		return toplevel != null;
	}
	
	@Override
	public Component getName(ItemStack itemStack) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(itemStack);
		if(toplevel == null) return UNKNOWN_WINDOW_TEXT;
		
		DesktopEntry entry = WaylandCraft.instance.xdgManager.forAppId(toplevel.appID);
		if(entry == null) return UNKNOWN_WINDOW_TEXT;
		
		String name = entry.name;
		if(name == null) return UNKNOWN_WINDOW_TEXT;
		
		return Component.literal(name);
	}
	
	@Override
	public void useTick(LivingEntity entity, ItemStack itemStack) {
		if(entity != Minecraft.getInstance().player) return;
		WaylandCraft.instance.startUsingWindowItem();
	}
	
}
