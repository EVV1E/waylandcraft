package dev.evvie.waylandcraft.datasync;

import java.util.ArrayList;
import java.util.HashSet;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.WindowMetadataPayload;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ServerDataSyncManager {
	
	public HashSet<WindowHandle> knownWindows = new HashSet<WindowHandle>();
	
	public void tick(MinecraftServer server) {
		ArrayList<WindowHandle> toRemove = new ArrayList<WindowHandle>();
		for(WindowHandle handle : knownWindows) {
			if(WaylandCraftUtils.getPlayerByUUID(server, handle.player()) == null) {
				System.out.println("SERVER: SENDING REMOVE");
				sendMetadataPayload(server, new WindowMetadataPayload(handle, WindowMetadataPayload.REMOVED, 0, 0, 0, 0));
				toRemove.add(handle);
			}
		}
		knownWindows.removeAll(toRemove);
	}
	
	public void sendMetadataPayload(MinecraftServer server, WindowMetadataPayload payload) {
		for(ServerPlayer player : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
	
	public void handleWindowMetadataPayload(WindowMetadataPayload payload, ServerPlayNetworking.Context ctx) {
		if(!payload.handle().matchesPlayer(ctx.player())) return;
		
		knownWindows.add(payload.handle());
		
		for(ServerPlayer player : PlayerLookup.all(ctx.server())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
	
}
