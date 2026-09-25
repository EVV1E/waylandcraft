package dev.evvie.waylandcraft;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.evvie.waylandcraft.item.ServerItemManager;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.item.WindowItemInteractionProvider;
import dev.evvie.waylandcraft.network.WaylandCraftNetworking;
import dev.evvie.waylandcraft.sharing.SharingServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(WaylandCraftCommon.MOD_ID)
public class WaylandCraftCommon {
	
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static WaylandCraftCommon instance;
	
	public @Nullable WindowItemInteractionProvider windowItemInteractionProvider = null;
	public ServerItemManager serverItemManager = new ServerItemManager();
	public SharingServer sharingServer = new SharingServer();
	
	public WaylandCraftCommon(IEventBus modBus) {
		instance = this;
		WindowItem.register(modBus);
		modBus.addListener(WaylandCraftNetworking::register);
		
		NeoForge.EVENT_BUS.addListener(LevelTickEvent.Pre.class, (event) -> {
			if(event.getLevel() instanceof ServerLevel level) {
				serverItemManager.onStartTick(level);
				sharingServer.tick(level);
			}
		});
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, (event) -> {
			if(event.getEntity() instanceof ServerPlayer player) sharingServer.onLogout(player);
		});
	}
	
}
