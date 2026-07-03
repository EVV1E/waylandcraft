package dev.evvie.waylandcraft.displays;

import dev.evvie.waylandcraft.math.WorldPlane;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractWindowDisplay {
	
	// World position of window
	public Vec3 pivot = new Vec3(0, 0, 0);
	
	// Window facing direction normal
	protected Vec3 normal = new Vec3(0, 0, 1);
	
	// Window orientation downwards vector, has to be orthogonal to `normal` and normalized
	protected Vec3 down = new Vec3(0, -1, 0);
	
	protected int width;
	protected int height;
	
	protected float pixelScale;
	
	public AbstractWindowDisplay() {
	}
	
	public abstract boolean isValid();
	
	public void rotate(Vec3 normal, Vec3 down) {
		this.normal = normal;
		this.down = down;
	}
	
	public Vec3 normal() {
		return normal;
	}
	
	public Vec3 down() {
		return down;
	}
	
	public Vec3 right() {
		return normal.cross(down);
	}
	
	public void setPixelScale(float scale) {
		this.pixelScale = scale;
	}
	
	public Vec3 localX() {
		return right().scale(pixelScale);
	}
	
	public Vec3 localY() {
		return down.scale(pixelScale);
	}
	
	// World coordinates of the window geometry origin
	public Vec3 origin() {
		return pivot.add(localX().scale(-width/2)).add(localY().scale(-height/2));
	}
	
	public WorldPlane getPlane() {
		return new WorldPlane(origin(), localX(), localY(), normal);
	}
	
	public Vec3 localToWorld(double x, double y, double z) {
		return getPlane().localToWorld(x, y, z);
	}
	
	public void moveOrigin(Vec3 pos) {
		pivot = pos.add(localX().scale(width/2)).add(localY().scale(height/2));
	}
	
	public ChunkPos getChunkPos() {
		return ChunkPos.containing(BlockPos.containing(pivot));
	}
	
	public void tick() {
	}
	
	public void extract(LevelExtractionContext ctx) {
	}
	
	public void render(LevelRenderContext ctx) {
	}
	
	/* Transform absolute world coordinates to surface-local pixel coordinates relative to geometry (0, 0)
	 * 
	 * The resulting vector is the (x, y) pixel location and the z value is the block distance normal to the plane.
	 */
	public Vec3 worldToLocal(Vec3 in) {
		return getPlane().worldToLocal(in);
	}
	
}
