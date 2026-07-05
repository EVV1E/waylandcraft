package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WindowMetadataPayload(WindowHandle handle, short flags, int width, int height, int xoff, int yoff) implements CustomPacketPayload {
	
	public static final Identifier WINDOW_METADATA_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_metadata");
	public static final CustomPacketPayload.Type<WindowMetadataPayload> TYPE = new CustomPacketPayload.Type<WindowMetadataPayload>(WINDOW_METADATA_PAYLOAD_ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, WindowMetadataPayload> CODEC = StreamCodec.composite(
			WindowHandle.STREAM_CODEC, WindowMetadataPayload::handle,
			WaylandCraftUtils.UNSIGNED_BYTE, WindowMetadataPayload::flags,
			ByteBufCodecs.INT, WindowMetadataPayload::width,
			ByteBufCodecs.INT, WindowMetadataPayload::height,
			ByteBufCodecs.INT, WindowMetadataPayload::xoff,
			ByteBufCodecs.INT, WindowMetadataPayload::yoff,
			WindowMetadataPayload::new
	);
	
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
