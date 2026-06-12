package dev.evvie.waylandcraft;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.item.WindowItemInteractionProvider;
import net.fabricmc.api.ModInitializer;

public class WaylandCraftCommon implements ModInitializer {
	
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static WaylandCraftCommon instance;
	
	public @Nullable WindowItemInteractionProvider windowItemInteractionProvider = null;
	
	@Override
	public void onInitialize() {
		instance = this;
		WindowItem.register();
	}
	
}
