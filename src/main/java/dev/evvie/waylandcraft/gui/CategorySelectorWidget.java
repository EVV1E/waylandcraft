package dev.evvie.waylandcraft.gui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
			Identifier.withDefaultNamespace("widget/button"),
			Identifier.withDefaultNamespace("widget/button_disabled"),
			Identifier.withDefaultNamespace("widget/button_highlighted")
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
	protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
		for(int i = 0; i < entries.size(); i++) {
			int bx = idxPosX(i);
			int by = idxPosY(i);
			
			context.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON_SPRITES.get(active, i == selected), bx, by, elementSize, elementSize);
			context.blitSprite(RenderPipelines.GUI_TEXTURED, entries.get(i).icon, bx + (elementSize - 15) / 2, by + (elementSize - 15) / 2, 15, 15);
			
			if(mouseX > bx && mouseY > by && mouseX < bx + elementSize && mouseY < by + elementSize) {
				context.setTooltipForNextFrame(entries.get(i).title, mouseX, mouseY);
			}
		}
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for(int i = 0; i < entries.size(); i++) {
			int bx = idxPosX(i);
			int by = idxPosY(i);
			
			if(event.x() > bx && event.y() > by && event.x() < bx + elementSize && event.y() < by + elementSize) {
				select(i);
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
	
	public static record Entry(Component title, Identifier icon) {}
	
}
