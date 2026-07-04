package dev.evvie.waylandcraft.datasync;

import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ImagePatch(short format, int x, int y, int width, int height, byte[] data) {
	
	public static final short FORMAT_RAW_RGBA = 1;
	
	public static final StreamCodec<RegistryFriendlyByteBuf, ImagePatch> CODEC = StreamCodec.composite(
			WaylandCraftUtils.UNSIGNED_BYTE, ImagePatch::format,
			ByteBufCodecs.INT, ImagePatch::x,
			ByteBufCodecs.INT, ImagePatch::y,
			ByteBufCodecs.INT, ImagePatch::width,
			ByteBufCodecs.INT, ImagePatch::height,
			ByteBufCodecs.BYTE_ARRAY, ImagePatch::data,
			ImagePatch::new
	);
	
}
