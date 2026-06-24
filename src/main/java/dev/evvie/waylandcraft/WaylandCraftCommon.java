package dev.evvie.waylandcraft;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.evvie.waylandcraft.item.ServerItemManager;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.item.WindowItemInteractionProvider;
import dev.evvie.waylandcraft.network.WaylandCraftNetworking;
import dev.evvie.waylandcraft.polymer.PolymerIntegration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public class WaylandCraftCommon implements ModInitializer {
	
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static WaylandCraftCommon instance;
	
	public @Nullable WindowItemInteractionProvider windowItemInteractionProvider = null;
	public ServerItemManager serverItemManager = new ServerItemManager();
	
	@Override
	public void onInitialize() {
		instance = this;
		WindowItem.register();
		WaylandCraftNetworking.register();

		if (FabricLoader.getInstance().isModLoaded("polymer-core")) {
			PolymerIntegration.registerResourcePackAssets();
		}

		ServerTickEvents.START_LEVEL_TICK.register(serverItemManager);
	}
	
}
