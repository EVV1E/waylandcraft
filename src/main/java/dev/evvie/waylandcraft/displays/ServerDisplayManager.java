package dev.evvie.waylandcraft.displays;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.ClientboundDisplayPayload;
import dev.evvie.waylandcraft.network.ServerboundDisplayPayload;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ServerDisplayManager {
	
	public ArrayList<ServerWindowDisplay> displays = new ArrayList<ServerWindowDisplay>();
	
	public void tick(MinecraftServer server) {
		ArrayList<ServerWindowDisplay> removeDisplays = new ArrayList<ServerWindowDisplay>();
		for(ServerWindowDisplay display : displays) {
			if(!WaylandCraftUtils.isHandleValid(server, display.handle)) {
				ClientboundDisplayPayload payload = new ClientboundDisplayPayload(display.handle, display.getProperties(), ClientboundDisplayPayload.REMOVED);
				sendDisplayPayload(server, payload);
				removeDisplays.add(display);
			}
		}
		displays.removeAll(removeDisplays);
		
		for(ServerWindowDisplay display : displays) {
			if(display.propertiesDirty) {
				ClientboundDisplayPayload payload = new ClientboundDisplayPayload(display.handle, display.getProperties(), ClientboundDisplayPayload.NONE);
				sendDisplayPayload(server, payload);
			}
		}
	}
	
	public void sendDisplayPayload(MinecraftServer server, ClientboundDisplayPayload payload) {
		for(ServerPlayer player : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
	
	public @Nullable ServerWindowDisplay getDisplay(WindowHandle handle) {
		for(ServerWindowDisplay display : displays) {
			if(display.handle.equals(handle)) return display;
		}
		return null;
	}
	
	public void handleDisplayPayload(ServerboundDisplayPayload payload, ServerPlayNetworking.Context ctx) {
		WindowHandle handle = WindowHandle.forPlayer(ctx.player(), payload.handle());
		
		ServerWindowDisplay display = getDisplay(handle);
		if(display == null) {
			display = new ServerWindowDisplay(handle, payload.properties());
			displays.add(display);
		}
		else {
			display.applyProperties(payload.properties());
		}
		display.propertiesDirty = true;
	}
	
}
