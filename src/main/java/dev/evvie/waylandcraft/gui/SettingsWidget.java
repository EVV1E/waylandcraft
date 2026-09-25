package dev.evvie.waylandcraft.gui;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.Util;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;

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
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		
		int x = getX();
		int y = getY();
		int width = getWidth();
		int height = getHeight();
		int totalElementWidth = ELEMENT_WIDTH;
		int textPad = (height - font.lineHeight) / 2;
		
		graphics.fill(x, y, x + width, y + height, FastColor.ARGB32.colorFromFloat(0.25f, 0.0f, 0.0f, 0.0f));
		
		graphics.enableScissor(x, y, x + width - totalElementWidth - textPad, y + height);
		graphics.drawString(font, getMessage(), x + textPad, y + textPad, FastColor.ARGB32.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f), active);
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
	
	// 1.21.1 has no double-click flag on mouse events, so detect it here
	private static final long DOUBLE_CLICK_MILLIS = 250;
	private long lastClickTime = 0;
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(!this.isActive()) return false;
		if(!isMouseOver(mouseX, mouseY)) return false;
		
		long now = Util.getMillis();
		boolean doubleClick = now - lastClickTime < DOUBLE_CLICK_MILLIS;
		lastClickTime = now;
		
		if(!doubleClick) control.onClick((int) mouseX, (int) mouseY, button);
		else control.onDoubleClick();
		return true;
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(!this.isActive()) return false;
		return control.onKeyPressed(keyCode, scanCode, modifiers);
	}
	
	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if(!this.isActive()) return false;
		return control.onKeyReleased(keyCode, scanCode, modifiers);
	}
	
	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if(!this.isActive()) return false;
		return control.onCharTyped(codePoint, modifiers);
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
	
	public abstract static class ControlElement {
		
		// Focus outline: light gray when focused, dark gray otherwise
		protected static int grayOutline(boolean focused) {
			float v = focused ? 1.0f : 0.5f;
			return FastColor.ARGB32.colorFromFloat(1.0f, v, v, v);
		}
		
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
		
		public abstract void extractControlElement(GuiGraphics graphics, int mouseX, int mouseY, float a);
		public abstract void saveValue();
		public void onClick(int mouseX, int mouseY, int button) {}
		public void onDoubleClick() {}
		public void onDrag() {}
		
		public void doClickSound() {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
		
		public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
			return false;
		}
		
		public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
			return false;
		}
		
		public boolean onCharTyped(char codePoint, int modifiers) {
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
		public void extractControlElement(GuiGraphics graphics, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			int width = getWidth();
			int height = getHeight();
			
			Font font = Minecraft.getInstance().font;
			Component text = getValue() ? Component.literal("ON") : Component.literal("OFF");
			
			graphics.fill(x, y, x + width, y + height, FastColor.ARGB32.colorFromFloat(0.6f, 0.0f, 0.0f, 0.0f));
			graphics.renderOutline(x, y, width, height, grayOutline(isFocused()));
			graphics.drawString(font, text, x + width / 2 - font.width(text) / 2, y + height / 2 - font.lineHeight / 2, FastColor.ARGB32.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f));
		}
		
		@Override
		public void onClick(int mouseX, int mouseY, int button) {
			if(isInside(mouseX, mouseY)) toggle();
		}
		
		@Override
		public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
			if(CommonInputs.selected(keyCode)) {
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
		public void extractControlElement(GuiGraphics graphics, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			int width = getWidth();
			int height = getHeight();
			
			Font font = Minecraft.getInstance().font;
			
			String str;
			if(entry == null) str = "" + getValue();
			else str = entry + "_";
			
			Component text = Component.literal(str);
			
			graphics.fill(x, y, x + width, y + height, FastColor.ARGB32.colorFromFloat(0.6f, 0.0f, 0.0f, 0.0f));
			graphics.renderOutline(x, y, width, height, grayOutline(isFocused()));
			
			int textWidth = font.width(text);
			int textHeight = font.lineHeight;
			int textX = x + width / 2 - textWidth / 2;
			int textY = y + height / 2 - textHeight / 2;
			
			graphics.drawString(font, text, textX, textY, FastColor.ARGB32.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f));
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
		public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
			boolean isEnter = keyCode == GLFW.GLFW_KEY_ENTER;
			boolean isBackspace = keyCode == GLFW.GLFW_KEY_BACKSPACE;
			
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
			
			int digit = -1;
			if(keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) digit = keyCode - GLFW.GLFW_KEY_0;
			else if(keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) digit = keyCode - GLFW.GLFW_KEY_KP_0;
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
		public void extractControlElement(GuiGraphics graphics, int mouseX, int mouseY, float a) {
			editBox.render(graphics, mouseX, mouseY, a);
		}
		
		@Override
		public void saveValue() {
			wlc.settingsManager.setTextSetting(settingName, editBox.getValue());
		}
		
		@Override
		public void onClick(int mouseX, int mouseY, int button) {
			super.onClick(mouseX, mouseY, button);
			editBox.onClick(mouseX, mouseY);
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
		public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
			if(keyCode == GLFW.GLFW_KEY_ENTER) {
				saveValue();
				return true;
			}
			
			return editBox.keyPressed(keyCode, scanCode, modifiers);
		}
		
		@Override
		public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
			return editBox.keyReleased(keyCode, scanCode, modifiers);
		}
		
		@Override
		public boolean onCharTyped(char codePoint, int modifiers) {
			return editBox.charTyped(codePoint, modifiers);
		}
		
	}
	
}
