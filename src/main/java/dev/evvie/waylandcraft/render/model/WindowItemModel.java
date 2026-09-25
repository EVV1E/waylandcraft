package dev.evvie.waylandcraft.render.model;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.item.WindowItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/* The window item shows the window's app icon when one is known.
 * models/item/window.json switches to models/item/window_icon.json when the
 * waylandcraft:window_state property is 1, and that builtin/entity model is drawn
 * by WindowSpecialRenderer.
 */
public class WindowItemModel {
	
	public static final ResourceLocation WINDOW_STATE = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_state");
	
	public static void register(IEventBus modBus) {
		modBus.addListener(FMLClientSetupEvent.class, (event) -> event.enqueueWork(() -> {
			ItemProperties.register(WindowItem.WINDOW.get(), WINDOW_STATE, (stack, level, entity, seed) -> getIcon(stack) != null ? 1.0f : 0.0f);
		}));
		
		modBus.addListener(RegisterClientExtensionsEvent.class, (event) -> event.registerItem(new IClientItemExtensions() {
			// Created on first use: the renderer's dependencies don't exist yet when extensions are registered
			private BlockEntityWithoutLevelRenderer renderer;
			
			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				if(renderer == null) renderer = new WindowSpecialRenderer();
				return renderer;
			}
		}, WindowItem.WINDOW.get()));
	}
	
	// App icon of the window an item refers to, or null
	public static ResourceLocation getIcon(ItemStack item) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(item);
		if(toplevel == null) return null;
		
		DesktopEntry entry = WaylandCraft.instance.xdgManager.forAppId(toplevel.appID);
		if(entry == null) return null;
		
		return entry.getIcon();
	}
	
}
