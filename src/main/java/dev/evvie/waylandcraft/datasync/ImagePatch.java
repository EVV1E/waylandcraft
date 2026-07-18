package dev.evvie.waylandcraft.datasync;

import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ImagePatch(short format, int x, int y, int width, int height, byte[] data) {
	
	public static final short FORMAT_RGBA = 1; // RGBA 8:8:8:8
	public static final short FORMAT_RGB = 2; // RGB 8:8:8
	public static final short FORMAT_RGBsA = 3; // Split RGB 8:8:8 and 2-bit alpha
	
	public static final StreamCodec<ByteBuf, ImagePatch> STREAM_CODEC = StreamCodec.composite(
			WaylandCraftUtils.UNSIGNED_BYTE, ImagePatch::format,
			ByteBufCodecs.UNSIGNED_SHORT, ImagePatch::x,
			ByteBufCodecs.UNSIGNED_SHORT, ImagePatch::y,
			ByteBufCodecs.UNSIGNED_SHORT, ImagePatch::width,
			ByteBufCodecs.UNSIGNED_SHORT, ImagePatch::height,
			ByteBufCodecs.BYTE_ARRAY, ImagePatch::data,
			ImagePatch::new
	);
	
}
