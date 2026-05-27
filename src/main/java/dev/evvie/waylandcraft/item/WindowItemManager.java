package dev.evvie.waylandcraft.item;

import java.util.LinkedHashSet;
import java.util.stream.StreamSupport;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class WindowItemManager {
	
	private WaylandCraft wlc;
	private LinkedHashSet<WLCToplevel> giveItems = new LinkedHashSet<WLCToplevel>();
	private LinkedHashSet<WLCToplevel> giveIfMissingItems = new LinkedHashSet<WLCToplevel>();
	
	public WindowItemManager(WaylandCraft wlc) {
		this.wlc = wlc;
	}
	
	public void giveItem(WLCToplevel toplevel) {
		giveItems.add(toplevel);
	}
	
	public void giveItemIfMissing(WLCToplevel toplevel) {
		giveIfMissingItems.add(toplevel);
	}
	
	public void giveItems(WLCToplevel... toplevels) {
		for(int i = 0; i < toplevels.length; i++) giveItem(toplevels[i]);
	}
	
	public void giveItemsIfMissing(WLCToplevel... toplevels) {
		for(int i = 0; i < toplevels.length; i++) giveItemIfMissing(toplevels[i]);
	}
	
	private boolean isToplevelValid(WLCToplevel toplevel) {
		return toplevel != null && toplevel.isMapped();
	}
	
	public void onServerTick(ServerLevel level) {
		if(wlc.bridge == null) return;
		if(level.players().size() < 1) return;
		
		level.players().forEach(player -> {
			Inventory inv = player.getInventory();
			
			giveItems.forEach(toplevel -> {
				ItemStack item = WindowItem.createItem(toplevel);
				player.addItem(item);
			});
			
			giveIfMissingItems.forEach((toplevel) -> {
				boolean foundToplevel = false;
				for(int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack item = inv.getItem(i);
					
					if(!item.is(WindowItem.WINDOW)) continue;
					if(WindowItem.getToplevel(item) == toplevel) {
						foundToplevel = true;
						break;
					}
				}
				
				if(!foundToplevel) {
					ItemStack item = WindowItem.createItem(toplevel);
					player.addItem(item);
				}
			});
			
			for(int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack item = inv.getItem(i);
				if(!item.is(WindowItem.WINDOW)) continue;
				
				WLCToplevel toplevel = WindowItem.getToplevel(item);
				if(toplevel == null) {
					inv.setItem(i, ItemStack.EMPTY);
				}
			}
		});
		
		giveItems.clear();
		giveIfMissingItems.clear();
		
		StreamSupport.stream(level.getAllEntities().spliterator(), false)
				.filter((e) -> e instanceof ItemEntity)
				.map((e) -> (ItemEntity) e)
				.filter((e) -> e.getItem().is(WindowItem.WINDOW))
				.filter((e) -> !isToplevelValid(WindowItem.getToplevel(e.getItem())))
				.filter((e) -> e.getAge() > 10)
				.forEach((e) -> {
					for(int i = 0; i < 10; i++) {
						double dx = ((level.getRandom().nextDouble() * 2) - 1) * 0.15;
						double dy = level.getRandom().nextDouble() * 0.2;
						double dz = ((level.getRandom().nextDouble() * 2) - 1) * 0.15;
						if(Minecraft.getInstance().level != null) Minecraft.getInstance().level.addParticle(ParticleTypes.FLAME, e.getX(), e.getY(), e.getZ(), dx, dy, dz);
					}
					e.discard();
				});
	}
	
}
