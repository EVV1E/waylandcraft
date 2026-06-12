package dev.evvie.waylandcraft.mixin;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;

import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.minecraft.server.level.ServerPlayer;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements IMyServerPlayer {
	
	private int itemGiveCooldown = 0;
	private ArrayList<Long> aliveWindows = new ArrayList<Long>();
	
	@Override
	public int getItemGiveCooldown() {
		return itemGiveCooldown;
	}
	
	@Override
	public void setItemGiveCooldown(int cooldown) {
		itemGiveCooldown = cooldown;
	}
	
	@Override
	public ArrayList<Long> getAliveWindows() {
		return aliveWindows;
	}
	
}
