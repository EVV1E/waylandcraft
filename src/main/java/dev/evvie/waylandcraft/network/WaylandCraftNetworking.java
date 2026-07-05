package dev.evvie.waylandcraft.network;

import java.util.ArrayList;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class WaylandCraftNetworking {
	
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ServerboundGiveItemsPayload.TYPE, ServerboundGiveItemsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ServerboundAliveWindowsPayload.TYPE, ServerboundAliveWindowsPayload.CODEC);
		
		PayloadTypeRegistry.serverboundPlay().register(DisplayPayload.TYPE, DisplayPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DisplayPayload.TYPE, DisplayPayload.CODEC);
		
		PayloadTypeRegistry.serverboundPlay().register(WindowMetadataPayload.TYPE, WindowMetadataPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WindowMetadataPayload.TYPE, WindowMetadataPayload.CODEC);
		
//		PayloadTypeRegistry.serverboundPlay().registerLarge(WindowDataPayload.TYPE, WindowDataPayload.CODEC, WindowDataPayload.MAX_SIZE);
//		PayloadTypeRegistry.clientboundPlay().registerLarge(WindowDataPayload.TYPE, WindowDataPayload.CODEC, WindowDataPayload.MAX_SIZE);
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAliveWindowsPayload.TYPE, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			ArrayList<Long> handles = plr.getAliveWindows();
			handles.clear();
			
			for(long handle : payload.handles()) {
				handles.add(handle);
			}
		});
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundGiveItemsPayload.TYPE, WaylandCraftCommon.instance.serverItemManager::handleGiveItemsPayload);
		ServerPlayNetworking.registerGlobalReceiver(DisplayPayload.TYPE, WaylandCraftCommon.instance.serverDisplayManager::handleDisplayPayload);
		ServerPlayNetworking.registerGlobalReceiver(WindowMetadataPayload.TYPE, WaylandCraftCommon.instance.serverDataSyncManager::handleWindowMetadataPayload);
	}
	
}
