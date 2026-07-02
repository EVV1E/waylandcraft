package dev.evvie.waylandcraft.displays;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.DisplayPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public class ServerDisplayManager {
	
	public ArrayList<ServerWindowDisplay> displays = new ArrayList<ServerWindowDisplay>();
	
	public void tick(MinecraftServer server) {
		for(ServerWindowDisplay display : displays) {
			if(display.propertiesDirty) {
				ServerPlayer player = display.getPlayer(server);
				sendDisplayPayload(player.level(), display.getChunkPos(), display.handle, display.getProperties(), DisplayPayload.NONE);
			}
		}
	}
	
	public void sendDisplayPayload(ServerLevel level, ChunkPos pos, WindowHandle handle, DisplayProperties properties, short flags) {
		DisplayPayload payload = new DisplayPayload(handle, properties, flags);
		
		for(ServerPlayer player : PlayerLookup.tracking(level, pos)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
	
	public @Nullable ServerWindowDisplay getDisplay(WindowHandle handle) {
		for(ServerWindowDisplay display : displays) {
			if(display.handle.equals(handle)) return display;
		}
		return null;
	}
	
	public void handleDisplayPayload(DisplayPayload payload, ServerPlayNetworking.Context ctx) {
		if(!payload.handle().matchesPlayer(ctx.player())) return;
		
		ServerWindowDisplay display = getDisplay(payload.handle());
		
		if(payload.removed()) {
			if(display == null) return;
			displays.remove(display);
			
			sendDisplayPayload(ctx.player().level(), display.getChunkPos(), display.handle, new DisplayProperties(), DisplayPayload.REMOVED);
			return;
		}
		
		if(display == null) {
			display = new ServerWindowDisplay(payload.handle(), payload.properties());
			displays.add(display);
		}
		else {
			display.applyProperties(payload.properties());
		}
		display.propertiesDirty = true;
	}
	
}
