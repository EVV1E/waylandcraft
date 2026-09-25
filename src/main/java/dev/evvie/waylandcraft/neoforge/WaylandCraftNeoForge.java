package dev.evvie.waylandcraft.neoforge;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WaylandCraftCommon.MOD_ID)
public class WaylandCraftNeoForge {
	
	public WaylandCraftNeoForge(IEventBus modBus) {
		WaylandCraftCommon.LOGGER.info("Initializing WaylandCraft");
	}
	
}
