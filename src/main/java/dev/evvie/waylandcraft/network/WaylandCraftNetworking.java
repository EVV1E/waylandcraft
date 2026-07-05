package dev.evvie.waylandcraft.network;

import java.util.ArrayList;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class WaylandCraftNetworking {
	
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ServerboundGiveItemsPayload.TYPE, ServerboundGiveItemsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAliveWindowsPayload.TYPE, ServerboundAliveWindowsPayload.CODEC);
		
		PayloadTypeRegistry.serverboundPlay().register(DisplayPayload.TYPE, DisplayPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DisplayPayload.TYPE, DisplayPayload.CODEC);
		
		PayloadTypeRegistry.serverboundPlay().registerLarge(WindowDataPayload.TYPE, WindowDataPayload.CODEC, WindowDataPayload.MAX_SIZE);
		PayloadTypeRegistry.clientboundPlay().registerLarge(WindowDataPayload.TYPE, WindowDataPayload.CODEC, WindowDataPayload.MAX_SIZE);
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAliveWindowsPayload.TYPE, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			ArrayList<Long> handles = plr.getAliveWindows();
			handles.clear();
			
			for(long handle : payload.handles()) {
				handles.add(handle);
			}
		});
		
		ServerPlayNetworking.registerGlobalReceiver(WindowDataPayload.TYPE, (payload, ctx) -> {
			if(!payload.handle().matchesPlayer(ctx.player())) return;
			
			for(ServerPlayer player : PlayerLookup.all(ctx.server())) {
				ServerPlayNetworking.send(player, payload);
			}
		});
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundGiveItemsPayload.TYPE, WaylandCraftCommon.instance.serverItemManager::handleGiveItemsPayload);
		ServerPlayNetworking.registerGlobalReceiver(DisplayPayload.TYPE, WaylandCraftCommon.instance.serverDisplayManager::handleDisplayPayload);
	}
	
}
