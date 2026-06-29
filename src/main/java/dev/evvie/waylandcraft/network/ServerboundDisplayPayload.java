package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.displays.DisplayProperties;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundDisplayPayload(long handle, DisplayProperties properties, /* unsigned byte */ short flags) implements CustomPacketPayload {
	
	public static final Identifier DISPLAY_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "display");
	
	public static final CustomPacketPayload.Type<ServerboundDisplayPayload> TYPE = new CustomPacketPayload.Type<ServerboundDisplayPayload>(DISPLAY_PAYLOAD_ID);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundDisplayPayload> CODEC = StreamCodec.composite(ByteBufCodecs.LONG, ServerboundDisplayPayload::handle, DisplayProperties.STREAM_CODEC, ServerboundDisplayPayload::properties, WaylandCraftUtils.UNSIGNED_BYTE, ServerboundDisplayPayload::flags, ServerboundDisplayPayload::new);
	
	// Bitmask flags
	public static final short NONE = 0;
	public static final short REMOVED = 1 << 0;
	
	public boolean removed() {
		return (flags & REMOVED) != 0;
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
