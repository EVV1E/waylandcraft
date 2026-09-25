package dev.evvie.waylandcraft.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WaylandCraftNeoForge.MOD_ID)
public class WaylandCraftNeoForge {

	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public WaylandCraftNeoForge(IEventBus modBus) {
		LOGGER.info("WaylandCraft NeoForge shell loaded");
	}

}
