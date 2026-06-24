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
		// Never actually sent — registering the type here (on both client and
		// server, since this runs from the common "main" entrypoint) is what
		// makes ServerPlayNetworking.canSend() usable as a per-player "does
		// this connection have WaylandCraft" check.
		PayloadTypeRegistry.clientboundPlay().register(ClientboundPresenceMarkerPayload.TYPE, ClientboundPresenceMarkerPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ServerboundGiveItemsPayload.TYPE, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			if(plr.getItemGiveCooldown() > 0) return;
			plr.setItemGiveCooldown(10);
			
			ArrayList<Long> handles = new ArrayList<Long>();
			for(long handle : payload.handles()) {
				if(handles.contains(handle)) continue;
				handles.add(handle);
			}
			
			if(payload.missingOnly()) WaylandCraftCommon.instance.serverItemManager.giveItemsIfMissing(ctx.player(), handles);
			else WaylandCraftCommon.instance.serverItemManager.giveItems(ctx.player(), handles);
		});
		
		ServerPlayNetworking.registerGlobalReceiver(ServerboundAliveWindowsPayload.TYPE, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			ArrayList<Long> handles = plr.getAliveWindows();
			handles.clear();
			
			for(long handle : payload.handles()) {
				handles.add(handle);
			}
		});
	}

	/**
	 * Client-only: registers a no-op receiver for ClientboundPresenceMarkerPayload.
	 * The payload itself is never sent — registering a receiver is what makes
	 * Fabric's networking handshake report this channel as supported, which is
	 * what ServerPlayNetworking.canSend() checks server-side per connection.
	 * Kept in its own method (called only from WaylandCraft.java's client
	 * constructor) so this never executes — and ClientPlayNetworking never
	 * gets touched — on the dedicated server.
	 */
	public static void registerClient() {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				ClientboundPresenceMarkerPayload.TYPE, (payload, ctx) -> {});
	}

}
