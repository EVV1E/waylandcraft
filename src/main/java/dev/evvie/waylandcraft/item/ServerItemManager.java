package dev.evvie.waylandcraft.item;

import java.util.List;
import java.util.stream.StreamSupport;

import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ServerItemManager implements ServerTickEvents.StartLevelTick {
	
	@Override
	public void onStartTick(ServerLevel level) {
		for(ServerPlayer player : level.players()) {
			Inventory inv = player.getInventory();
			for(int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack item = inv.getItem(i);
				if(!item.is(WindowItem.WINDOW)) continue;
				
				Long handle = item.get(WindowItem.WINDOW_HANDLE);
				if(!isHandleValid(level, handle)) {
					inv.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		
		for(ServerPlayer player : level.players()) {
			IMyServerPlayer plr = (IMyServerPlayer) player;
			int itemGiveCooldown = plr.getItemGiveCooldown();
			if(itemGiveCooldown > 0) {
				plr.setItemGiveCooldown(itemGiveCooldown - 1);
			}
		}
		
		StreamSupport.stream(level.getAllEntities().spliterator(), false)
			.filter((e) -> e instanceof ItemEntity)
			.map((e) -> (ItemEntity) e)
			.filter((e) -> e.getItem().is(WindowItem.WINDOW))
			.filter((e) -> !isHandleValid(level, e.getItem().get(WindowItem.WINDOW_HANDLE)))
			.filter((e) -> e.getAge() > 10)
			.forEach((e) -> {
				level.sendParticles(ParticleTypes.FLAME, false, false, e.getX(), e.getY(), e.getZ(), 10, 0.15, 0.2, 0.15, 0.1);
				e.discard();
			});
	}
	
	private boolean isHandleValid(ServerLevel level, Long handle) {
		if(handle == null) return false;
		
		for(ServerPlayer player : level.players()) {
			IMyServerPlayer plr = (IMyServerPlayer) player;
			if(plr.getAliveWindows().contains(handle)) return true;
		}
		
		return false;
	}
	
	public void giveItems(ServerPlayer player, List<Long> handles) {
		for(Long handle : handles) giveItem(player, handle);
	}
	
	public void giveItemsIfMissing(ServerPlayer player, List<Long> handles) {
		for(Long handle : handles) giveItemIfMissing(player, handle);
	}
	
	public void giveItem(ServerPlayer player, long handle) {
		ItemStack item = createItem(handle);
		player.addItem(item);
	}
	
	public void giveItemIfMissing(ServerPlayer player, long handle) {
		Inventory inv = player.getInventory();
		
		boolean foundToplevel = false;
		for(int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack item = inv.getItem(i);
			
			if(!item.is(WindowItem.WINDOW)) continue;
			if(item.get(WindowItem.WINDOW_HANDLE) == handle) {
				foundToplevel = true;
				break;
			}
		}
		
		if(!foundToplevel) {
			ItemStack item = createItem(handle);
			player.addItem(item);
		}
	}
	
	public static ItemStack createItem(long handle) {
		ItemStack stack = new ItemStack(WindowItem.WINDOW, 1);
		stack.set(WindowItem.WINDOW_HANDLE, handle);
		return stack;
	}
	
}
