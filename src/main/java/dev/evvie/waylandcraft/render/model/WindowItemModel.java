package dev.evvie.waylandcraft.render.model;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

public class WindowItemModel {
	
	public static void register() {
		SelectItemModelProperties.ID_MAPPER.put(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_state"), WindowStateProperty.TYPE);
		SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window"), WindowSpecialRenderer.Unbaked.MAP_CODEC);
	}
	
}
