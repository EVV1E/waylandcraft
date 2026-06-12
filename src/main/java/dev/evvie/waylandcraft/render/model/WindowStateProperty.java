package dev.evvie.waylandcraft.render.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.render.model.WindowStateProperty.WindowState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public record WindowStateProperty() implements SelectItemModelProperty<WindowState> {
	
	public static final Codec<WindowState> VALUE_CODEC = WindowState.CODEC;
	public static final SelectItemModelProperty.Type<WindowStateProperty, WindowState> TYPE = SelectItemModelProperty.Type.create(MapCodec.unit(new WindowStateProperty()), VALUE_CODEC);
	
	@Override
	public WindowState get(ItemStack item, ClientLevel clientLevel, LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(item);
		if(toplevel == null) return WindowState.NONE;
		
		DesktopEntry entry = WaylandCraft.instance.xdgManager.forAppId(toplevel.appID);
		if(entry == null) return WindowState.NONE;
		
		Identifier icon = entry.getIcon();
		if(icon == null) return WindowState.NONE;
		
		return WindowState.ICON;
	}
	
	@Override
	public Codec<WindowState> valueCodec() {
		return VALUE_CODEC;
	}
	
	@Override
	public Type<? extends SelectItemModelProperty<WindowState>, WindowState> type() {
		return TYPE;
	}
	
	public static enum WindowState implements StringRepresentable {
		NONE("none"), ICON("icon"), BROKEN("broken");
		
		public static final Codec<WindowState> CODEC = StringRepresentable.fromEnum(WindowState::values);
		
		private final String name;
		
		private WindowState(String name) {
			this.name = name;
		}
		
		@Override
		public String getSerializedName() {
			return name;
		}
		
	}
	
}
