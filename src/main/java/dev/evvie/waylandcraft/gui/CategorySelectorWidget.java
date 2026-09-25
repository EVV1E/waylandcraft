package dev.evvie.waylandcraft.gui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CategorySelectorWidget extends AbstractWidget {
	
	private int selected = -1;
	private List<Entry> entries;
	private int elementSize;
	private Consumer<Integer> selectAction;
	
	public CategorySelectorWidget(Component component, Consumer<Integer> selectAction, List<Entry> entries) {
		super(0, 0, 0, 0, component);
		this.selectAction = selectAction;
		this.entries = entries;
	}
	
	public void setElementSize(int s) {
		this.elementSize = s;
	}
	
	public int getSelected() {
		return selected;
	}
	
	public void select(int idx) {
		selected = idx;
		selectAction.accept(idx);
	}
	
	public void unselect() {
		selected = -1;
	}
	
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
		return null;
	}
	
	private static final WidgetSprites BUTTON_SPRITES = new WidgetSprites(
			ResourceLocation.withDefaultNamespace("widget/button"),
			ResourceLocation.withDefaultNamespace("widget/button_disabled"),
			ResourceLocation.withDefaultNamespace("widget/button_highlighted")
	);
	
	private int elementsPerColumn() {
		return getHeight() / elementSize;
	}
	
	private int idxPosX(int idx) {
		return getX() + (entries.size() - 1) / elementsPerColumn() * elementSize - idx / elementsPerColumn() * elementSize;
	}
	
	private int idxPosY(int idx) {
		return getY() + (idx % elementsPerColumn()) * elementSize;
	}
	
	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
		for(int i = 0; i < entries.size(); i++) {
			int bx = idxPosX(i);
			int by = idxPosY(i);
			
			context.blitSprite(BUTTON_SPRITES.get(active, i == selected), bx, by, elementSize, elementSize);
			context.blitSprite(entries.get(i).icon, bx + (elementSize - 15) / 2, by + (elementSize - 15) / 2, 15, 15);
			
			if(mouseX > bx && mouseY > by && mouseX < bx + elementSize && mouseY < by + elementSize) {
				if(Minecraft.getInstance().screen != null) Minecraft.getInstance().screen.setTooltipForNextRenderPass(entries.get(i).title);
			}
		}
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for(int i = 0; i < entries.size(); i++) {
			int bx = idxPosX(i);
			int by = idxPosY(i);
			
			if(mouseX > bx && mouseY > by && mouseX < bx + elementSize && mouseY < by + elementSize) {
				select(i);
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
	
	public static record Entry(Component title, ResourceLocation icon) {}
	
}
