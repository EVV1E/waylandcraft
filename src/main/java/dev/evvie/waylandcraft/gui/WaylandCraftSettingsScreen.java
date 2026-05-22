package dev.evvie.waylandcraft.gui;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaylandCraftSettingsScreen extends Screen {
	
	private WaylandCraft wlc;
	
	public WaylandCraftSettingsScreen(WaylandCraft wlc) {
		super(Component.literal("Waylandcraft Settings"));
		
		this.wlc = wlc;
	}
	
	@Override
	protected void init() {
	}
	
}
