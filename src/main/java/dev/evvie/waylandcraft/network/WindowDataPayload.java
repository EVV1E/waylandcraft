package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.datasync.ImagePatch;
import dev.evvie.waylandcraft.item.WindowHandle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WindowDataPayload(WindowHandle handle, ImagePatch patch) implements CustomPacketPayload {
	
	public static final int MAX_SIZE = (3 * Long.BYTES + 4 * Integer.BYTES) + (2048 * 2048 * 4);
	
	public static final Identifier WINDOW_DATA_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_data");
	public static final CustomPacketPayload.Type<WindowDataPayload> TYPE = new CustomPacketPayload.Type<WindowDataPayload>(WINDOW_DATA_PAYLOAD_ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, WindowDataPayload> CODEC = StreamCodec.composite(
			WindowHandle.STREAM_CODEC, WindowDataPayload::handle,
			ImagePatch.STREAM_CODEC, WindowDataPayload::patch,
			WindowDataPayload::new
	);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
