package dev.evvie.waylandcraft.gui;

import java.util.ArrayList;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaylandCraftSettingsScreen extends Screen {
	
	private WaylandCraft wlc;
	private ScrollableLayout layout;
	
	private ArrayList<AbstractSettingsWidget> settingsWidgets = new ArrayList<>();
	
	public WaylandCraftSettingsScreen(WaylandCraft wlc) {
		super(Component.literal("Waylandcraft Settings"));
		
		this.wlc = wlc;
	}
	
	private AbstractSettingsWidget createBooleanSettingsWidget(String name, Component message) {
		AbstractSettingsWidget widget = new AbstractSettingsWidget.BooleanSettingsWidget(wlc, name, message);
		widget.init();
		settingsWidgets.add(widget);
		return widget;
	}
	
	private AbstractSettingsWidget createIntSettingsWidget(String name, Component message) {
		AbstractSettingsWidget widget = new AbstractSettingsWidget.IntSettingsWidget(wlc, name, message);
		widget.init();
		settingsWidgets.add(widget);
		return widget;
	}
	
	@Override
	protected void init() {
		createSettings();
		
		LinearLayout content = LinearLayout.vertical().spacing(4);
		for(AbstractSettingsWidget widget : settingsWidgets) {
			content.addChild(widget);
		}
		
		layout = new ScrollableLayout(minecraft, content, height - 50);
		layout.setPosition(width / 2 - AbstractSettingsWidget.WIDTH / 2 - 25 / 2, 25);
		layout.arrangeElements();
		layout.visitWidgets((w) -> addRenderableWidget(w));
	}
	
	@Override
	protected void repositionElements() {
		super.repositionElements();
		layout.arrangeElements();
	}
	
	private void createSettings() {
		settingsWidgets.clear();
		
		createIntSettingsWidget("pixelsPerBlock", Component.literal("Window display pixels per block"));
		createBooleanSettingsWidget("test", Component.literal("Test value"));
	}
	
}
