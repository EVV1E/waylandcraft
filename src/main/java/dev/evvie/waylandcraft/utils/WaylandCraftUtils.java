package dev.evvie.waylandcraft.utils;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class WaylandCraftUtils {
	
	public static Vec3 getPosition(Entity entity) {
		float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 pos = new Vec3(
			Mth.lerp(partialTicks, entity.xo, entity.getX()),
			Mth.lerp(partialTicks, entity.yo, entity.getY()) + entity.getEyeHeight(),
			Mth.lerp(partialTicks, entity.zo, entity.getZ())
		);
		return pos;
	}
	
	public static Vec3 getLookVector(Entity entity) {
		float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		float yaw = entity.getViewYRot(partialTicks);
		float pitch = entity.getViewXRot(partialTicks);
		
		Quaternionf rotation = new Quaternionf();
		rotation.rotationYXZ(-yaw * Mth.PI / 180.0f, pitch * Mth.PI / 180.0f, 0.0f);
		
		Vec3 look = new Vec3(new Vector3f(0, 0, 1).rotate(rotation));
		return look;
	}
	
	public static Vec3 getUpVector(Entity entity) {
		float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		float yaw = entity.getViewYRot(partialTicks);
		float pitch = entity.getViewXRot(partialTicks);
		
		Quaternionf rotation = new Quaternionf();
		rotation.rotationYXZ(-yaw * Mth.PI / 180.0f, pitch * Mth.PI / 180.0f, 0.0f);
		
		Vec3 up = new Vec3(new Vector3f(0, 1, 0).rotate(rotation));
		return up;
	}
	
}
