package dev.evvie.waylandcraft.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * Joins a screen's row of small square icon buttons (skin, accessibility,
 * Realms notifications, Mindful Darkness' daytime toggle, etc) by finding the
 * largest group of same-width SpriteIconButtons sharing a Y coordinate,
 * appending a new button after the rightmost one at matching spacing, then
 * recentering the whole row.
 */
public final class IconRowUtils {

	public static final int ICON_ROW_BUTTON_WIDTH = 20;

	private IconRowUtils() {}

	public static List<AbstractWidget> findIconRow(Iterable<? extends GuiEventListener> children) {
		Map<Integer, List<AbstractWidget>> byY = new HashMap<>();
		for(GuiEventListener listener : children) {
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

	/**
	 * Positions {@code button} after the rightmost widget in {@code row} at
	 * matching spacing, then recenters the whole row (including the new
	 * button) around its original midpoint. Does nothing but leave
	 * {@code button} at its current position if {@code row} is empty.
	 */
	public static void joinRow(List<AbstractWidget> row, AbstractWidget button) {
		if(row.isEmpty()) return;

		AbstractWidget rightmost = row.get(0);
		for(AbstractWidget widget : row) {
			if(widget.getX() > rightmost.getX()) rightmost = widget;
		}

		int spacing = computeSpacing(row);
		button.setPosition(rightmost.getX() + rightmost.getWidth() + spacing, rightmost.getY());

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

}
