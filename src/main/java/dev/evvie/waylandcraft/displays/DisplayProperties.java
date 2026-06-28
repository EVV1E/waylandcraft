package dev.evvie.waylandcraft.displays;

import dev.evvie.waylandcraft.utils.UnitVec3;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public record DisplayProperties(Vec3 pivot, Vec3 normal, Vec3 down, int width, int height, int geometryX, int geometryY, float pixelScale) {
	
	public static final StreamCodec<RegistryFriendlyByteBuf, DisplayProperties> STREAM_CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, DisplayProperties::pivot,
			UnitVec3.STREAM_CODEC, DisplayProperties::normal,
			UnitVec3.STREAM_CODEC, DisplayProperties::down,
			ByteBufCodecs.INT, DisplayProperties::width,
			ByteBufCodecs.INT, DisplayProperties::height,
			ByteBufCodecs.INT, DisplayProperties::geometryX,
			ByteBufCodecs.INT, DisplayProperties::geometryY,
			ByteBufCodecs.FLOAT, DisplayProperties::pixelScale,
			DisplayProperties::new
	);
	
}
