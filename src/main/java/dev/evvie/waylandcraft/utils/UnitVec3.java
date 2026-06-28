package dev.evvie.waylandcraft.utils;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/* Helper class for sending normalized vectors over the network
 * The three components are encoded as 16-bit integers with the assumption that all components
 * are between -1 and 1.
 */
public class UnitVec3 {
	
	public static final StreamCodec<ByteBuf, Vec3> STREAM_CODEC = StreamCodec.of(UnitVec3::write, UnitVec3::read);
	
	/* Deserialize Vec3 from ByteBuf as UnitVec3
	 * The Vec3 is always renormalized.
	 */
	public static Vec3 read(final ByteBuf input) {
		short sx = input.readShort();
		short sy = input.readShort();
		short sz = input.readShort();
		
		double vx = ((double) sx) / Short.MAX_VALUE;
		double vy = ((double) sy) / Short.MAX_VALUE;
		double vz = ((double) sz) / Short.MAX_VALUE;
		
		return new Vec3(vx, vy, vz).normalize();
	}
	
	/* Serialize Vec3 to ByteBuf as UnitVec3
	 * Value SHOULD be normalized. NaN components are encoded as zero.
	 * Components are clamped between -1 and 1.
	 */
	public static void write(final ByteBuf output, final Vec3 value) {
		double vx = sanitize(value.x);
		double vy = sanitize(value.y);
		double vz = sanitize(value.z);
		
		short sx = (short) (vx * Short.MAX_VALUE);
		short sy = (short) (vy * Short.MAX_VALUE);
		short sz = (short) (vz * Short.MAX_VALUE);
		
		output.writeShort(sx);
		output.writeShort(sy);
		output.writeShort(sz);
	}
	
	private static double sanitize(double d) {
		return Double.isNaN(d) ? 0.0 : Math.clamp(d, -1.0, 1.0);
	}
	
}
