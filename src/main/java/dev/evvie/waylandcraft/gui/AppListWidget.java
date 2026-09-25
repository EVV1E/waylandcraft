package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.evvie.waylandcraft.desktop.DesktopEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class AppListWidget extends AbstractContainerWidget {
	
	private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
	private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");
	
	private static final int SLOT_GAPS = 2;
	public static final int ELEMENT_WIDTH = 200 + 2;
	public static final int ELEMENT_HEIGHT = 32 + 2;
	
	public static final int DEFAULT_WIDTH = ELEMENT_WIDTH + 8 + 6;
	
	private ArrayList<AppWidget> children = new ArrayList<AppWidget>();
	private Consumer<DesktopEntry> launchAction;
	
	private int maxScroll = 0;
	private int scroll = 0;
	private int contentHeight = 0;
	
	public AppListWidget(Consumer<DesktopEntry> launchAction, Component title) {
		super(0, 0, 0, 0, title);
		this.launchAction = launchAction;
	}
	
	public void setEntries(List<DesktopEntry> entries) {
		children.clear();
		for(DesktopEntry entry : entries) {
			children.add(new AppWidget(entry, launchAction));
		}
		scroll = 0;
		rearrangeChildren();
	}
	
	private void rearrangeChildren() {
		contentHeight = children.size() * (ELEMENT_HEIGHT + SLOT_GAPS) - SLOT_GAPS;
		maxScroll = Math.max(contentHeight - height, 0);
		
		if(scroll < 0) scroll = 0;
		if(scroll > maxScroll) scroll = maxScroll;
		
		int x = getX();
		int y = getY();
		y -= scroll;
		
		int width = ELEMENT_WIDTH;
		for(int i = 0; i < children.size(); i++) {
			AppWidget widget = children.get(i);
			widget.setRectangle(ELEMENT_WIDTH, ELEMENT_HEIGHT, x + width / 2 - ELEMENT_WIDTH / 2, y + i * (ELEMENT_HEIGHT + SLOT_GAPS));
		}
		
	}
	
	@Override
	public void setSize(int width, int height) {
		// HACK: Override width to add additional bounds for scrollbar
		super.setSize(width + 8 + 6, height);
	}
	
	private void scrollTo(AppWidget widget) {
		boolean topCondition = widget.getY() >= getY();
		boolean bottomCondition = widget.getBottom() <= getBottom();
		if(topCondition && bottomCondition) {
			/* Widget already in view */
			return;
		}
		
		int top = children.get(0).getY();
		int bottomScroll = widget.getBottom() - top - height;
		int topScroll = widget.getY() - top;
		
		if(!bottomCondition) scroll = bottomScroll;
		else scroll = topScroll;
		
		if(scroll < 0) scroll = 0;
		if(scroll > maxScroll) scroll = maxScroll;
	}
	
	@Override
	public void setFocused(GuiEventListener guiEventListener) {
		super.setFocused(guiEventListener);
		if(guiEventListener instanceof AppWidget) scrollTo((AppWidget) guiEventListener);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (int) scrollY * 10;
		if(scroll < 0) scroll = 0;
		if(scroll > maxScroll) scroll = maxScroll;
		
		return true;
	}
	
	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
		rearrangeChildren();
		
		int x = getX();
		int y = getY();
		int width = ELEMENT_WIDTH;
		int height = getHeight();
		
		context.renderOutline(x - 1, y - 1, width + 2, height + 2, Color.black.getRGB());
		context.renderOutline(x - 2, y - 2, width + 4, height + 4, Color.black.getRGB());
		
		context.enableScissor(x, y, x + width, y + height);
		
		for(AppWidget child : children) {
			child.render(context, mouseX, mouseY, partialTicks);
		}
		
		context.disableScissor();
		
		int scrollerX = x + width + 8;
		int scrollerY = y - 2;
		int scrollerWidth = 6;
		int scrollerHeight = height + 4;
		
		int scrollerSize = Math.round(height / (float) contentHeight * scrollerHeight);
		int scrollerPos = Math.round(scroll / (float) contentHeight * scrollerHeight);
		
		if(contentHeight <= height) {
			scrollerSize = scrollerHeight;
			scrollerPos = 0;
		}
		
		context.blitSprite(SCROLLER_BACKGROUND_SPRITE, scrollerX, scrollerY, scrollerWidth, scrollerHeight);
		context.blitSprite(SCROLLER_SPRITE, scrollerX, scrollerY + scrollerPos, scrollerWidth, scrollerSize);
	}
	
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double accumX, double accumY) {
		int y = getY();
		int height = getHeight();
		
		int scrollerY = y - 2;
		int scrollerHeight = height + 4;
		
		scroll = (int) (((mouseY - scrollerY) / scrollerHeight) * contentHeight - height / 2);
		if(scroll < 0) scroll = 0;
		if(scroll > maxScroll) scroll = maxScroll;
		
		return true;
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int x = getX();
		int y = getY();
		int width = ELEMENT_WIDTH;
		int height = getHeight();
		
		int scrollerX = x + width + 8;
		int scrollerY = y - 2;
		int scrollerWidth = 6;
		int scrollerHeight = height + 4;
		
		if(mouseX >= scrollerX && mouseX <= scrollerX + scrollerWidth && mouseY >= scrollerY && mouseY <= scrollerY + scrollerHeight) {
			return true;
		}
		
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
	
	@Override
	public List<? extends GuiEventListener> children() {
		return children;
	}
	
}
