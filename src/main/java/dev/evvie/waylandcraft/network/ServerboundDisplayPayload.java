package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.displays.DisplayProperties;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundDisplayPayload(long handle, DisplayProperties properties) implements CustomPacketPayload {
	
	public static final Identifier DISPLAY_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "display");
	
	public static final CustomPacketPayload.Type<ServerboundDisplayPayload> TYPE = new CustomPacketPayload.Type<ServerboundDisplayPayload>(DISPLAY_PAYLOAD_ID);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundDisplayPayload> CODEC = StreamCodec.composite(ByteBufCodecs.LONG, ServerboundDisplayPayload::handle, DisplayProperties.STREAM_CODEC, ServerboundDisplayPayload::properties, ServerboundDisplayPayload::new);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
