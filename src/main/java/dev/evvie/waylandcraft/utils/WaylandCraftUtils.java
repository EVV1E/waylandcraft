package dev.evvie.waylandcraft.utils;

import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class WaylandCraftUtils {
	
	public static Vec3 getPosition(Entity entity) {
		float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		Vec3 pos = new Vec3(
			Mth.lerp(partialTicks, entity.xo, entity.getX()),
			Mth.lerp(partialTicks, entity.yo, entity.getY()) + entity.getEyeHeight(),
			Mth.lerp(partialTicks, entity.zo, entity.getZ())
		);
		return pos;
	}
	
	public static Vec3 getLookVector(Entity entity) {
		float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		float yaw = entity.getViewYRot(partialTicks);
		float pitch = entity.getViewXRot(partialTicks);
		
		Quaternionf rotation = new Quaternionf();
		rotation.rotationYXZ(-yaw * Mth.PI / 180.0f, pitch * Mth.PI / 180.0f, 0.0f);
		
		Vec3 look = new Vec3(new Vector3f(0, 0, 1).rotate(rotation));
		return look;
	}
	
	public static Vec3 getUpVector(Entity entity) {
		float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		float yaw = entity.getViewYRot(partialTicks);
		float pitch = entity.getViewXRot(partialTicks);
		
		Quaternionf rotation = new Quaternionf();
		rotation.rotationYXZ(-yaw * Mth.PI / 180.0f, pitch * Mth.PI / 180.0f, 0.0f);
		
		Vec3 up = new Vec3(new Vector3f(0, 1, 0).rotate(rotation));
		return up;
	}
	
	public static ServerPlayer getPlayer(ServerLevel level, UUID id) {
		for(ServerPlayer player : level.players()) {
			UUID pid = WindowHandle.getPlayerUUID(player);
			if(pid.equals(id)) return player;
		}
		return null;
	}
	
	public static boolean isHandleValid(ServerLevel level, WindowHandle handle) {
		if(handle == null) return false;
		if(WaylandCraftCommon.instance.sharingServer.isTestPattern(handle)) return true;
		
		ServerPlayer player = getPlayer(level, handle.player());
		if(player == null) return false;
		
		return ((IMyServerPlayer) player).getAliveWindows().contains(handle.handle());
	}
	
	public static boolean isHandleValid(MinecraftServer server, WindowHandle handle) {
		for(ServerLevel level : server.getAllLevels()) {
			if(isHandleValid(level, handle)) return true;
		}
		return false;
	}
	
}
