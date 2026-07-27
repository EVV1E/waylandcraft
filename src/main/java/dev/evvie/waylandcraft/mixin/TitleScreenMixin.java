package dev.evvie.waylandcraft.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.gui.WaylandCraftSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {

	private static final int ICON_ROW_BUTTON_WIDTH = 20;

	protected TitleScreenMixin() {
		super(null);
	}

	@Inject(method = "init", at = @At("TAIL"))
	public void addWaylandCraftButton(CallbackInfo info) {
		List<AbstractWidget> row = findIconRow();

		SpriteIconButton button = SpriteIconButton
				.builder(Component.literal("waylandcraft"), (_) -> {Minecraft.getInstance().gui.setScreen(new WaylandCraftSettingsScreen(WaylandCraft.instance));}, true)
				.sprite(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "logo"), 16, 16)
				.width(ICON_ROW_BUTTON_WIDTH)
				.build();

		if(row.isEmpty()) {
			// Fallback: no recognizable icon row found, place near the bottom-left corner.
			button.setPosition(width / 2 - 124, height - 56);
			this.addRenderableWidget(button);
			return;
		}

		AbstractWidget rightmost = row.get(0);
		for(AbstractWidget widget : row) {
			if(widget.getX() > rightmost.getX()) rightmost = widget;
		}

		int spacing = computeSpacing(row);
		int y = rightmost.getY();
		int x = rightmost.getX() + rightmost.getWidth() + spacing;
		button.setPosition(x, y);
		this.addRenderableWidget(button);

		int moveX = (button.getWidth() + spacing) / 2;
		for(AbstractWidget widget : row) {
			widget.setX(widget.getX() - moveX);
		}
		button.setX(button.getX() - moveX);
	}

	private static int computeSpacing(List<AbstractWidget> row) {
		if(row.size() < 2) return 4;

		int minX = Integer.MAX_VALUE;
		int maxXEdge = Integer.MIN_VALUE;
		int totalWidth = 0;
		for(AbstractWidget widget : row) {
			minX = Math.min(minX, widget.getX());
			maxXEdge = Math.max(maxXEdge, widget.getX() + widget.getWidth());
			totalWidth += widget.getWidth();
		}

		return Math.max(1, ((maxXEdge - minX) - totalWidth) / (row.size() - 1));
	}

	/**
	 * Finds the largest group of same-width {@link SpriteIconButton}s sharing
	 * a Y coordinate — the row of small square icon buttons vanilla and other
	 * mods (e.g. accessibility, realms notifications, Mindful Darkness'
	 * daytime toggle) place near the bottom-left of the title screen.
	 */
	private List<AbstractWidget> findIconRow() {
		Map<Integer, List<AbstractWidget>> byY = new HashMap<>();
		for(net.minecraft.client.gui.components.events.GuiEventListener listener : this.children()) {
			if(!(listener instanceof SpriteIconButton widget)) continue;
			if(widget.getWidth() != ICON_ROW_BUTTON_WIDTH) continue;
			byY.computeIfAbsent(widget.getY(), (_) -> new ArrayList<>()).add(widget);
		}

		List<AbstractWidget> best = List.of();
		for(List<AbstractWidget> group : byY.values()) {
			if(group.size() > best.size()) best = group;
		}
		return best;
	}

}
