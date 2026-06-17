package dev.evvie.waylandcraft.gui;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;

public class SettingsWidget extends AbstractWidget {
	
	// Standard widget width, height
	public static final int WIDTH = 300;
	public static final int HEIGHT = 30;
	
	// Width of the interactable element in the widget
	private static final int ELEMENT_WIDTH = 100;
	
	public final ControlElement control;
	protected WaylandCraft wlc;
	
	private SettingsWidget(WaylandCraft instance, ControlElement control, Component message) {
		super(0, 0, WIDTH, HEIGHT, message);
		this.control = control;
		this.wlc = instance;
	}
	
	public static SettingsWidget createBooleanWidget(WaylandCraft instance, String settingName, Component message) {
		BooleanControlElement control = new BooleanControlElement(instance, settingName);
		return new SettingsWidget(instance, control, message);
	}
	
	public static SettingsWidget createIntWidget(WaylandCraft instance, String settingName, Component message) {
		IntControlElement control = new IntControlElement(instance, settingName);
		return new SettingsWidget(instance, control, message);
	}
	
	public static SettingsWidget createTextWidget(WaylandCraft instance, String settingName, Component message) {
		TextControlElement control = new TextControlElement(instance, settingName);
		return new SettingsWidget(instance, control, message);
	}
	
	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		
		int x = getX();
		int y = getY();
		int width = getWidth();
		int height = getHeight();
		int totalElementWidth = ELEMENT_WIDTH;
		int textPad = (height - font.lineHeight) / 2;
		
		graphics.fill(x, y, x + width, y + height, ARGB.black(0.25f));
		
		graphics.enableScissor(x, y, x + width - totalElementWidth - textPad, y + height);
		graphics.text(font, message, x + textPad, y + textPad, ARGB.white(1.0f), active);
		graphics.disableScissor();
		
		int elemPad = 5;
		
		int elemX = x + width - totalElementWidth + elemPad;
		int elemY = y + elemPad;
		int elemWidth = totalElementWidth - elemPad * 2;
		int elemHeight = height - elemPad * 2;
		
		control.setPosSize(elemX, elemY, elemWidth, elemHeight);
		control.setFocused(this.isFocused());
		control.extractControlElement(graphics, mouseX, mouseY, a);
	}
	
	public void saveValue() {
		control.saveValue();
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if(!this.isActive()) return false;
		if(!isMouseOver(event.x(), event.y())) return false;
		
		if(!doubleClick) control.onClick((int) event.x(), (int) event.y(), event.buttonInfo());
		else control.onDoubleClick();
		return true;
	}
	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if(!this.isActive()) return false;
		return control.onKeyPressed(event);
	}
	
	@Override
	public boolean keyReleased(KeyEvent event) {
		if(!this.isActive()) return false;
		return control.onKeyReleased(event);
	}
	
	@Override
	public boolean charTyped(CharacterEvent event) {
		if(!this.isActive()) return false;
		return control.onCharTyped(event);
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
	
	public abstract static class ControlElement {
		
		public final String settingName;
		protected WaylandCraft wlc;
		
		private int x;
		private int y;
		private int width;
		private int height;
		private boolean focused;
		
		public ControlElement(WaylandCraft wlc, String settingName) {
			this.settingName = settingName;
			this.wlc = wlc;
		}
		
		public void setPosSize(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
		
		public void setFocused(boolean focused) {
			this.focused = focused;
		}
		
		public int getX() {
			return x;
		}
		
		public int getY() {
			return y;
		}
		
		public int getWidth() {
			return width;
		}
		
		public int getHeight() {
			return height;
		}
		
		public boolean isFocused() {
			return focused;
		}
		
		public boolean isInside(int testX, int testY) {
			return x <= testX && testX <= x + width && y <= testY && testY <= y + height;
		}
		
		public abstract void extractControlElement(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
		public abstract void saveValue();
		public void onClick(int mouseX, int mouseY, MouseButtonInfo buttonInfo) {}
		public void onDoubleClick() {}
		public void onDrag() {}
		
		public void doClickSound() {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
		
		public boolean onKeyPressed(KeyEvent event) {
			return false;
		}
		
		public boolean onKeyReleased(KeyEvent event) {
			return false;
		}
		
		public boolean onCharTyped(CharacterEvent event) {
			return false;
		}
		
	}
	
	public static class BooleanControlElement extends ControlElement {
		
		public BooleanControlElement(WaylandCraft wlc, String settingName) {
			super(wlc, settingName);
		}
		
		private boolean getValue() {
			return wlc.settingsManager.getBooleanSetting(settingName);
		}
		
		private void setValue(boolean value) {
			wlc.settingsManager.setBooleanSetting(settingName, value);
		}
		
		@Override
		public void saveValue() {
			// Left blank. This widget automatically sets the value on click
		}
		
		private void toggle() {
			setValue(!getValue());
			doClickSound();
		}
		
		@Override
		public void extractControlElement(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			int width = getWidth();
			int height = getHeight();
			
			Font font = Minecraft.getInstance().font;
			Component text = getValue() ? Component.literal("ON") : Component.literal("OFF");
			
			graphics.fill(x, y, x + width, y + height, ARGB.black(0.6f));
			graphics.outline(x, y, width, height, ARGB.gray(isFocused() ? 1.0f : 0.5f));
			graphics.text(font, text, x + width / 2 - font.width(text) / 2, y + height / 2 - font.lineHeight / 2, ARGB.white(1.0f));
		}
		
		@Override
		public void onClick(int mouseX, int mouseY, MouseButtonInfo buttonInfo) {
			if(isInside(mouseX, mouseY)) toggle();
		}
		
		@Override
		public boolean onKeyPressed(KeyEvent event) {
			if(event.isSelection()) {
				toggle();
				return true;
			}
			return false;
		}
		
	}
	
	public static class IntControlElement extends ControlElement {
		
		private @Nullable String entry = null;
		
		public IntControlElement(WaylandCraft wlc, String settingName) {
			super(wlc, settingName);
		}
		
		private int getValue() {
			return wlc.settingsManager.getIntSetting(settingName);
		}
		
		private void setValue(int value) {
			wlc.settingsManager.setIntSetting(settingName, value);
		}
		
		@Override
		public void extractControlElement(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			int width = getWidth();
			int height = getHeight();
			
			Font font = Minecraft.getInstance().font;
			
			String str;
			if(entry == null) str = "" + getValue();
			else str = entry + "_";
			
			Component text = Component.literal(str);
			
			graphics.fill(x, y, x + width, y + height, ARGB.black(0.6f));
			graphics.outline(x, y, width, height, ARGB.gray(isFocused() ? 1.0f : 0.5f));
			
			int textWidth = font.width(text);
			int textHeight = font.lineHeight;
			int textX = x + width / 2 - textWidth / 2;
			int textY = y + height / 2 - textHeight / 2;
			
			graphics.text(font, text, textX, textY, ARGB.white(1.0f));
		}
		
		private void stopEntry() {
			if(entry == null) return;
			if(entry.length() == 0) return;
			setValue(Integer.parseInt(entry));
			entry = null;
		}
		
		@Override
		public void saveValue() {
			stopEntry();
		}
		
		@Override
		public void setFocused(boolean focused) {
			if(!focused && isFocused()) {
				// Focus lost
				stopEntry();
			}
			
			super.setFocused(focused);
		}
		
		@Override
		public void onDoubleClick() {
			if(entry == null) {
				entry = "" + getValue();
			}
		}
		
		@Override
		public boolean onKeyPressed(KeyEvent event) {
			boolean isEnter = event.key() == GLFW.GLFW_KEY_ENTER;
			boolean isBackspace = event.key() == GLFW.GLFW_KEY_BACKSPACE;
			
			if(isEnter && entry != null) {
				stopEntry();
				return true;
			}
			if((isBackspace || isEnter) && entry == null) {
				entry = "";
				return true;
			}
			if(isBackspace && entry != null) {
				if(entry.length() > 0) entry = entry.substring(0, entry.length() - 1);
				return true;
			}
			
			int digit = event.getDigit();
			if(digit != -1) {
				if(entry != null) entry += digit;
				else entry = "" + digit;
				return true;
			}
			return false;
		}
		
	}
	
	public static class TextControlElement extends ControlElement {
		
		private EditBox editBox;
		
		public TextControlElement(WaylandCraft wlc, String settingName) {
			super(wlc, settingName);
			
			editBox = new EditBox(Minecraft.getInstance().font, getWidth(), getHeight(), Component.literal(settingName));
			editBox.insertText(getSavedValue());
			editBox.moveCursorTo(0, false);
		}
		
		private String getSavedValue() {
			return wlc.settingsManager.getTextSetting(settingName);
		}
		
		@Override
		public void setPosSize(int x, int y, int width, int height) {
			super.setPosSize(x, y, width, height);
			editBox.setRectangle(width, height, x, y);
		}
		
		@Override
		public void extractControlElement(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			editBox.extractRenderState(graphics, mouseX, mouseY, a);
		}
		
		@Override
		public void saveValue() {
			wlc.settingsManager.setTextSetting(settingName, editBox.getValue());
		}
		
		@Override
		public void onClick(int mouseX, int mouseY, MouseButtonInfo buttonInfo) {
			super.onClick(mouseX, mouseY, buttonInfo);
			editBox.onClick(new MouseButtonEvent(mouseX, mouseY, buttonInfo), false);
		}
		
		@Override
		public void setFocused(boolean focused) {
			if(!focused && isFocused()) {
				// Focus lost
				saveValue();
			}
			
			editBox.setFocused(focused);
			super.setFocused(focused);
		}
		
		@Override
		public boolean onKeyPressed(KeyEvent event) {
			if(event.key() == GLFW.GLFW_KEY_ENTER) {
				saveValue();
				return true;
			}
			
			return editBox.keyPressed(event);
		}
		
		@Override
		public boolean onKeyReleased(KeyEvent event) {
			return editBox.keyReleased(event);
		}
		
		@Override
		public boolean onCharTyped(CharacterEvent event) {
			return editBox.charTyped(event);
		}
		
	}
	
}
