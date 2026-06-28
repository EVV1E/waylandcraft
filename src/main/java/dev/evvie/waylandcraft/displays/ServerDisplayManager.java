package dev.evvie.waylandcraft.displays;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.ServerboundDisplayPayload;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;

public class ServerDisplayManager {
	
	public ArrayList<ServerWindowDisplay> displays = new ArrayList<ServerWindowDisplay>();
	
	public void tick(MinecraftServer server) {
		ArrayList<ServerWindowDisplay> removeDisplays = new ArrayList<ServerWindowDisplay>();
		for(ServerWindowDisplay display : displays) {
			if(!WaylandCraftUtils.isHandleValid(server, display.handle)) {
				System.out.println("REMOVING DISPLAY: " + display.handle);
				removeDisplays.add(display);
			}
		}
		displays.removeAll(removeDisplays);
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
			System.out.println("ADDING DISPLAY: " + display.handle);
		}
		else {
			display.applyProperties(payload.properties());
		}
		display.propertiesDirty = true;
	}
	
}
