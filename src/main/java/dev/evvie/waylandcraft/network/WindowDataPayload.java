package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowHandle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WindowDataPayload(WindowHandle handle, int width, int height, int xoff, int yoff, byte[] data) implements CustomPacketPayload {
	
	public static final Identifier WINDOW_DATA_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_data");
	public static final CustomPacketPayload.Type<WindowDataPayload> TYPE = new CustomPacketPayload.Type<WindowDataPayload>(WINDOW_DATA_PAYLOAD_ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, WindowDataPayload> CODEC = StreamCodec.composite(
			WindowHandle.STREAM_CODEC, WindowDataPayload::handle,
			ByteBufCodecs.INT, WindowDataPayload::width,
			ByteBufCodecs.INT, WindowDataPayload::height,
			ByteBufCodecs.INT, WindowDataPayload::xoff,
			ByteBufCodecs.INT, WindowDataPayload::yoff,
			ByteBufCodecs.BYTE_ARRAY, WindowDataPayload::data,
			WindowDataPayload::new
	);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
