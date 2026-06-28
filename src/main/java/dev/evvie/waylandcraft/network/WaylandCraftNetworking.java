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
		PayloadTypeRegistry.serverboundPlay().register(ServerboundDisplayPayload.TYPE, ServerboundDisplayPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientboundDisplayPayload.TYPE, ClientboundDisplayPayload.CODEC);
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAliveWindowsPayload.TYPE, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			ArrayList<Long> handles = plr.getAliveWindows();
			handles.clear();
			
			for(long handle : payload.handles()) {
				handles.add(handle);
			}
		});
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundGiveItemsPayload.TYPE, WaylandCraftCommon.instance.serverItemManager::handleGiveItemsPayload);
		ServerPlayNetworking.registerGlobalReceiver(ServerboundDisplayPayload.TYPE, WaylandCraftCommon.instance.serverDisplayManager::handleDisplayPayload);
	}
	
}
