package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.lwjgl.glfw.GLFW;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public abstract class AbstractSettingsWidget extends AbstractContainerWidget {
	
	public static final int WIDTH = 300;
	public static final int HEIGHT = 30;
	
	public final String settingName;
	protected WaylandCraft wlc;
	protected ArrayList<AbstractWidget> children = new ArrayList<AbstractWidget>();
	protected FrameLayout layout = new FrameLayout(WIDTH, HEIGHT);
	
	private static final LayoutSettings LEFT_CENTERED = LayoutSettings.defaults().alignVerticallyMiddle().alignHorizontallyLeft().paddingLeft(10);
	private static final LayoutSettings RIGHT_CENTERED = LayoutSettings.defaults().alignVerticallyMiddle().alignHorizontallyRight().paddingRight(10);
	
	public AbstractSettingsWidget(WaylandCraft instance, String settingName, Component message) {
		super(0, 0, WIDTH, HEIGHT, message, AbstractScrollArea.defaultSettings(0));
		this.settingName = settingName;
		this.wlc = instance;
	}
	
	protected <T extends AbstractWidget> T addChild(T child, LayoutSettings layoutSettings) {
		children.add(child);
		layout.addChild(child, layoutSettings);
		return child;
	}
	
	protected <T extends AbstractWidget> T addChild(T child) {
		children.add(child);
		layout.addChild(child);
		return child;
	}
	
	protected abstract void init();
	
	@Override
	public List<? extends GuiEventListener> children() {
		return children;
	}
	
	@Override
	protected int contentHeight() {
		return HEIGHT;
	}
	
	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		layout.setPosition(getX(), getY());
		layout.arrangeElements();
		
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), ARGB.color(0.2f, Color.black.getRGB()));
		
		for(AbstractWidget child : children) {
			child.extractRenderState(graphics, mouseX, mouseY, a);
		}
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
	
	public static class BooleanSettingsWidget extends AbstractSettingsWidget {
		
		private Button button;
		
		public BooleanSettingsWidget(WaylandCraft instance, String settingName, Component message) {
			super(instance, settingName, message);
		}
		
		protected boolean enabled() {
			return wlc.settings.getBooleanSetting(settingName);
		}
		
		private Component buttonMessage() {
			return enabled() ? Component.literal("ON") : Component.literal("OFF");
		}
		
		@Override
		public void init() {
			addChild(new StringWidget(message, Minecraft.getInstance().font), LEFT_CENTERED);
			button = Button.builder(buttonMessage(), (_) -> {
				wlc.settingsManager.setBooleanSetting(settingName, !enabled());
				button.setMessage(buttonMessage());
			}).width(75).build();
			addChild(button, RIGHT_CENTERED);
		}
		
	}
	
	public static class IntSettingsWidget extends AbstractSettingsWidget {
		
		private EditBox editBox;
		
		public IntSettingsWidget(WaylandCraft instance, String settingName, Component message) {
			super(instance, settingName, message);
		}
		
		private boolean isDigit(int codepoint) {
			return codepoint >= 0x30 && codepoint <= 0x39;
		}
		
		private OptionalInt parseInt(String s) {
			try {
				return OptionalInt.of(Integer.parseInt(s));
			} catch(NumberFormatException e) {
				return OptionalInt.empty();
			}
		}
		
		protected int getSettingValue() {
			return wlc.settings.getIntSetting(settingName);
		}
		
		@Override
		protected void init() {
			addChild(new StringWidget(message, Minecraft.getInstance().font), LEFT_CENTERED);
			editBox = new EditBox(Minecraft.getInstance().font, 110, 25, message) {
				@Override
				public boolean charTyped(CharacterEvent event) {
					if(event.isAllowedChatCharacter() && canConsumeInput() && (event.codepoint() < 0x30 || event.codepoint() > 0x39)) return true;
					return super.charTyped(event);
				}
				
				@Override
				public boolean keyPressed(KeyEvent event) {
					if(canConsumeInput() && event.key() == GLFW.GLFW_KEY_ENTER) {
						setFocused(false);
						OptionalInt result = parseInt(getValue());
						if(getValue().codePoints().anyMatch((c) -> !isDigit(c)) || result.isEmpty()) {
							this.insertText("" + getSettingValue());
							return true;
						}
						wlc.settingsManager.setIntSetting(settingName, result.getAsInt());
						return true;
					}
					return super.keyPressed(event);
				}
				
				@Override
				public void setFocused(boolean focused) {
					super.setFocused(focused);
					this.moveCursorToEnd(false);
					if(focused) {
						this.setHighlightPos(0);
					}
				}
			};
			editBox.setValue("" + getSettingValue());
			addChild(editBox, RIGHT_CENTERED);
		}
		
	}
	
	// Currently has a bug where dragging sometimes doesn't work
	public static class SliderIntSettingsWidget extends AbstractSettingsWidget {
		
		private AbstractSliderButton slider;
		private final int minValue;
		private final int maxValue;
		
		public SliderIntSettingsWidget(WaylandCraft instance, String settingName, Component message, int minValue, int maxValue) {
			super(instance, settingName, message);
			this.minValue = minValue;
			this.maxValue = maxValue;
		}
		
		protected int getValue() {
			return wlc.settings.getIntSetting(settingName);
		}
		
		protected double getValueRatio() {
			int value = getValue();
			return (value - minValue) / (double) (maxValue - minValue);
		}
		
		protected int convertRatioToValue(double ratio) {
			return (int) (ratio * (maxValue - minValue) + minValue);
		}
		
		private Component sliderMessage(double ratio) {
			return Component.literal("" + convertRatioToValue(ratio));
		}
		
		@Override
		protected void init() {
			addChild(new StringWidget(message, Minecraft.getInstance().font), LEFT_CENTERED);
			slider = new AbstractSliderButton(0, 0, 100, 20, Component.empty(), getValueRatio()) {
				
				@Override
				protected void updateMessage() {
					this.message = sliderMessage(this.value);
				}
				
				@Override
				protected void applyValue() {
				}
				
				private void actuallyApplyValue() {
					wlc.settingsManager.setIntSetting(settingName, convertRatioToValue(this.value));
					WaylandCraft.LOGGER.info("SET VALUE: " + convertRatioToValue(this.value));
				}
				
				@Override
				public void onRelease(MouseButtonEvent event) {
					super.onRelease(event);
					actuallyApplyValue();
				}
				
				@Override
				public void onClick(MouseButtonEvent event, boolean doubleClick) {
					super.onClick(event, doubleClick);
					actuallyApplyValue();
				}
				
			};
			slider.setMessage(sliderMessage(getValueRatio()));
			addChild(slider, RIGHT_CENTERED);
		}
		
	}
	
}
