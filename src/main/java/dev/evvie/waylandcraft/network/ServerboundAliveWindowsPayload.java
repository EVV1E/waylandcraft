package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundAliveWindowsPayload(long[] handles) implements CustomPacketPayload {
	
	public static final Identifier ALIVE_WINDOWS_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "alive_windows");
	
	public static final CustomPacketPayload.Type<ServerboundAliveWindowsPayload> TYPE = new CustomPacketPayload.Type<ServerboundAliveWindowsPayload>(ALIVE_WINDOWS_PAYLOAD_ID);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAliveWindowsPayload> CODEC = StreamCodec.composite(ByteBufCodecs.LONG_ARRAY, ServerboundAliveWindowsPayload::handles, ServerboundAliveWindowsPayload::new);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
