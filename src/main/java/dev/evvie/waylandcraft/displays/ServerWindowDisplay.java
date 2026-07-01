package dev.evvie.waylandcraft.displays;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public class ServerWindowDisplay {
	
	public final WindowHandle handle;
	public boolean propertiesDirty = false;
	
	public Vec3 pivot;
	public Vec3 normal;
	public Vec3 down;
	
	public int width = 0;
	public int height = 0;
	
	public int geometryX = 0;
	public int geometryY = 0;
	
	public float pixelScale;
	
	public ServerWindowDisplay(WindowHandle handle, DisplayProperties properties) {
		this.handle = handle;
		this.applyProperties(properties);
	}
	
	public DisplayProperties getProperties() {
		return new DisplayProperties(pivot, normal, down, width, height, geometryX, geometryY, pixelScale);
	}
	
	public ServerPlayer getPlayer(MinecraftServer server) {
		return WaylandCraftUtils.getPlayerByUUID(server, handle.player());
	}
	
	public ChunkPos getChunkPos() {
		return ChunkPos.containing(BlockPos.containing(pivot));
	}
	
	public void applyProperties(DisplayProperties properties) {
		this.pivot = properties.pivot();
		this.normal = properties.normal();
		this.down = properties.down();
		this.width = properties.width();
		this.height = properties.height();
		this.geometryX = properties.geometryX();
		this.geometryY = properties.geometryY();
		this.pixelScale = properties.pixelScale();
	}
	
}
