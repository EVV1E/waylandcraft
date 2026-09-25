package dev.evvie.waylandcraft.network;

import java.util.ArrayList;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class WaylandCraftNetworking {
	
	// ByteBufCodecs has no long[] codec in 1.21.1
	public static final StreamCodec<FriendlyByteBuf, long[]> LONG_ARRAY = StreamCodec.of(FriendlyByteBuf::writeLongArray, FriendlyByteBuf::readLongArray);
	
	public static void register(RegisterPayloadHandlersEvent event) {
		// Optional: the client works on servers without the mod, which just don't get window items
		PayloadRegistrar registrar = event.registrar("1").optional();
		
		registrar.playToServer(ServerboundAliveWindowsPayload.TYPE, ServerboundAliveWindowsPayload.CODEC, (payload, ctx) -> {
			IMyServerPlayer plr = (IMyServerPlayer) ctx.player();
			ArrayList<Long> handles = plr.getAliveWindows();
			handles.clear();
			
			for(long handle : payload.handles()) {
				handles.add(handle);
			}
		});
		
		registrar.playToServer(ServerboundGiveItemsPayload.TYPE, ServerboundGiveItemsPayload.CODEC, WaylandCraftCommon.instance.serverItemManager::handleGiveItemsPayload);
	}
	
	// Client side: sends the payload if the server has the mod installed
	public static void sendToServer(CustomPacketPayload payload) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if(connection == null || !connection.hasChannel(payload)) return;
		PacketDistributor.sendToServer(payload);
	}
	
}
