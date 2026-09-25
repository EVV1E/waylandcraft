package dev.evvie.waylandcraft.item;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import dev.evvie.waylandcraft.network.ServerboundGiveItemsPayload;
import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import dev.evvie.waylandcraft.utils.WaylandCraftUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerItemManager {
	
	public void onStartTick(ServerLevel level) {
		for(ServerPlayer player : level.players()) {
			Inventory inv = player.getInventory();
			for(int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack item = inv.getItem(i);
				if(!item.is(WindowItem.WINDOW)) continue;
				
				WindowHandle handle = item.get(WindowItem.WINDOW_HANDLE);
				if(!WaylandCraftUtils.isHandleValid(level, handle)) {
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
			.filter((e) -> !WaylandCraftUtils.isHandleValid(level, e.getItem().get(WindowItem.WINDOW_HANDLE)))
			.filter((e) -> e.getAge() > 10)
			.forEach((e) -> {
				level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY(), e.getZ(), 10, 0.15, 0.2, 0.15, 0.1);
				e.discard();
			});
	}
	
	public void handleGiveItemsPayload(ServerboundGiveItemsPayload payload, IPayloadContext ctx) {
		ServerPlayer player = (ServerPlayer) ctx.player();
		IMyServerPlayer plr = (IMyServerPlayer) player;
		if(plr.getItemGiveCooldown() > 0) return;
		plr.setItemGiveCooldown(10);
		
		ArrayList<Long> handles = new ArrayList<Long>();
		for(long handle : payload.handles()) {
			if(handles.contains(handle)) continue;
			handles.add(handle);
		}
		
		if(payload.missingOnly()) giveItemsIfMissing(player, handles);
		else giveItems(player, handles);
	}
	
	public void giveItems(ServerPlayer player, List<Long> handles) {
		for(Long handle : handles) giveItem(player, handle);
	}
	
	public void giveItemsIfMissing(ServerPlayer player, List<Long> handles) {
		for(Long handle : handles) giveItemIfMissing(player, handle);
	}
	
	public void giveItem(ServerPlayer player, long handle) {
		ItemStack item = createItem(player, handle);
		player.addItem(item);
	}
	
	public void giveItemIfMissing(ServerPlayer player, long handle) {
		Inventory inv = player.getInventory();
		WindowHandle searched = WindowHandle.forPlayer(player, handle);
		
		boolean foundToplevel = false;
		for(int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack item = inv.getItem(i);
			WindowHandle data = item.get(WindowItem.WINDOW_HANDLE);
			
			if(!item.is(WindowItem.WINDOW)) continue;
			if(data != null && data.equals(searched)) {
				foundToplevel = true;
				break;
			}
		}
		
		if(!foundToplevel) {
			ItemStack item = createItem(player, handle);
			player.addItem(item);
		}
	}
	
	public static ItemStack createItem(ServerPlayer player, long handle) {
		ItemStack stack = new ItemStack(WindowItem.WINDOW.get(), 1);
		stack.set(WindowItem.WINDOW_HANDLE, WindowHandle.forPlayer(player, handle));
		return stack;
	}
	
}
