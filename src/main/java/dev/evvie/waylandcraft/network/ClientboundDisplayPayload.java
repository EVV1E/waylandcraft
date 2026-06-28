package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.displays.DisplayProperties;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundDisplayPayload(WindowHandle handle, DisplayProperties properties, /* unsigned byte */ short flags) implements CustomPacketPayload {
	
	public static final Identifier REMOTE_DISPLAY_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "remote_display");
	
	public static final CustomPacketPayload.Type<ClientboundDisplayPayload> TYPE = new CustomPacketPayload.Type<ClientboundDisplayPayload>(REMOTE_DISPLAY_PAYLOAD_ID);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDisplayPayload> CODEC = StreamCodec.composite(WindowHandle.STREAM_CODEC, ClientboundDisplayPayload::handle, DisplayProperties.STREAM_CODEC, ClientboundDisplayPayload::properties, WaylandCraftUtils.UNSIGNED_BYTE, ClientboundDisplayPayload::flags, ClientboundDisplayPayload::new);
	
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
