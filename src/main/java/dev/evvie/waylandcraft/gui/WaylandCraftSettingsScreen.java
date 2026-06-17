package dev.evvie.waylandcraft.gui;

import java.util.ArrayList;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.settings.WaylandCraftSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaylandCraftSettingsScreen extends Screen {
	
	private WaylandCraft wlc;
	private ScrollableLayout layout;
	
	private ArrayList<SettingsWidget> settingsWidgets = new ArrayList<>();
	
	public WaylandCraftSettingsScreen(WaylandCraft wlc) {
		super(Component.literal("Waylandcraft Settings"));
		
		this.wlc = wlc;
	}
	
	@Override
	protected void init() {
		createSettings();
		
		FrameLayout header = new FrameLayout(0, 0, width, 25);
		header.addChild(new StringWidget(title, font), LayoutSettings.defaults().align(0.5f, 0.5f));
		header.arrangeElements();
		header.visitWidgets((w) -> addRenderableWidget(w));
		
		LinearLayout content = LinearLayout.vertical().spacing(4);
		for(SettingsWidget widget : settingsWidgets) {
			content.addChild(widget);
		}
		
		layout = new ScrollableLayout(minecraft, content, height - 75);
		layout.setPosition(width / 2 - SettingsWidget.WIDTH / 2 - 25 / 2, 50);
		layout.arrangeElements();
		layout.visitWidgets((w) -> addRenderableWidget(w));
	}
	
	@Override
	public void removed() {
		for(SettingsWidget widget : settingsWidgets) {
			widget.saveValue();
		}
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
	
	@Override
	protected void repositionElements() {
		super.repositionElements();
		layout.arrangeElements();
	}
	
	public void createBooleanSettingsWidget(String settingName, Component message) {
		SettingsWidget widget = SettingsWidget.createBooleanWidget(wlc, settingName, message);
		settingsWidgets.add(widget);
	}
	
	public void createIntSettingsWidget(String settingName, Component message) {
		SettingsWidget widget = SettingsWidget.createIntWidget(wlc, settingName, message);
		settingsWidgets.add(widget);
	}
	
	public void createTextSettingsWidget(String settingName, Component message) {
		SettingsWidget widget = SettingsWidget.createTextWidget(wlc, settingName, message);
		settingsWidgets.add(widget);
	}
	
	private void createSettings() {
		settingsWidgets.clear();
		
		createIntSettingsWidget(WaylandCraftSettings.PIXELS_PER_BLOCK, Component.literal("Window display pixels per block"));
		createBooleanSettingsWidget(WaylandCraftSettings.WINDOW_ANTIALIASING, Component.literal("Window in world antialiasing"));
		createBooleanSettingsWidget(WaylandCraftSettings.FOCUS_ON_HOVER, Component.literal("Focus windows when hovered"));
		createTextSettingsWidget(WaylandCraftSettings.TERMINAL_CHOICE, Component.literal("Default terminal"));
	}
	
}
