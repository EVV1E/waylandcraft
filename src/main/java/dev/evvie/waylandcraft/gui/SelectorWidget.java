package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class SelectorWidget<T> extends AbstractWidget {
	
	private ArrayList<SelectorButton<T>> buttons = new ArrayList<SelectorButton<T>>();
	
	// Currently selected element, should always be either null or an element assigned to a button
	private T selected = null;
	
	@SuppressWarnings("unchecked")
	public SelectorWidget(int x, int y, int width, int height) {
		super(x, y, width, height, Component.empty());
		
		setEntries((T[]) new Object[0]);
	}
	
	private int unrestrictedButtonWidth() {
		return getWidth() / 5;
	}
	
	public void setEntries(T[] entries) {
		buttons.clear();
		
		int x = getX();
		int y = getY();
		int height = getHeight();
		
		for(int i = 0; i < entries.length; i++) {
			SelectorButton<T> button = new SelectorButton<T>(this, x, y, unrestrictedButtonWidth(), height);
			button.element = entries[i];
			buttons.add(button);
		}
		
		if(entries.length == 0) {
			SelectorButton<T> button = new SelectorButton<T>(this, x, y, unrestrictedButtonWidth(), height);
			button.element = null;
			buttons.add(button);
		}
		
		selectionCheck();
		arrangeButtons();
	}
	
	private void arrangeButtons() {
		int x = getX();
		int y = getY();
		int width = getWidth();
		int height = getHeight();
		
		int buttonWidth = unrestrictedButtonWidth();
		if(buttons.size() * buttonWidth > width) {
			buttonWidth = width / buttons.size();
		}
		
		for(int i = 0; i < buttons.size(); i++) {
			SelectorButton<T> button = buttons.get(i);
			button.setX(x);
			button.setY(y);
			button.setWidth(buttonWidth);
			button.setHeight(height);
			x += buttonWidth;
		}
	}
	
	public abstract Component titleForElement(T element);
	public abstract @Nullable ResourceLocation iconForElement(T element);
	public abstract boolean elementDimColor(T element);
	
	public T selection() {
		return selected;
	}
	
	// Maintains selected element property
	private void selectionCheck() {
		if(!buttons.stream().anyMatch((b) -> b.element == selected)) {
			selected = null;
		}
	}
	
	public void select(T element) {
		this.selected = element;
		selectionCheck();
	}
	
	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
		for(int i = 0; i < buttons.size(); i++) {
			SelectorButton<T> b = buttons.get(i);
			
			b.visible = this.visible;
			b.selected = b.element == selected;
			
			if(b.element != null) {
				b.setMessage(titleForElement(b.element));
				b.dimColor = elementDimColor(b.element);
				b.icon = iconForElement(b.element);
			}
			else {
				b.setMessage(Component.empty());
				b.dimColor = false;
				b.icon = null;
			}
			
			b.render(context, mouseX, mouseY, partialTicks);
		}
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(!(this.active && this.visible)) return false;
		
		for(SelectorButton<T> b : buttons) {
			if(b.mouseClicked(mouseX, mouseY, button)) return true;
		}
		
		return false;
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
	
	private static class SelectorButton<T> extends Button {
		
		public T element = null;
		public boolean selected = false;
		public boolean dimColor = false;
		public ResourceLocation icon = null;
		
		@SuppressWarnings("unchecked")
		public SelectorButton(SelectorWidget<T> widget, int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), (b) -> {widget.select(((SelectorButton<T>) b).element);}, (c) -> c.get());
		}
		
		private static final WidgetSprites SPRITES = new WidgetSprites(
				ResourceLocation.withDefaultNamespace("widget/button"),
				ResourceLocation.withDefaultNamespace("widget/button_disabled"),
				ResourceLocation.withDefaultNamespace("widget/button_highlighted")
		);
		
		@Override
		protected void renderWidget(GuiGraphics context, int i, int j, float f) {
			int x = getX();
			int y = getY();
			int width = getWidth();
			int height = getHeight();
			
			Color color = dimColor ? Color.lightGray : Color.white;
			Font font = Minecraft.getInstance().font;
			
			context.blitSprite(SPRITES.get(active, selected), x, y, width, height);
			context.enableScissor(x + 2, y, x + width - 2, y + height);
			
			int xoff = x + 2;
			int iconSize = height - 4;
			
			if(icon != null) {
				RenderUtils.blitFull(context, icon, xoff, y + 2, xoff + iconSize, y + 2 + iconSize);
				xoff += iconSize + 2;
			}
			
			context.drawString(font, getMessage(), xoff, y + height / 2 - font.lineHeight / 2, color.getRGB());
			context.disableScissor();
		}
		
	}
	
}
