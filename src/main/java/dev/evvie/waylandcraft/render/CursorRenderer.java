package dev.evvie.waylandcraft.render;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WindowDisplay.DisplayHitResult;
import dev.evvie.waylandcraft.bridge.IconSurface;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class CursorRenderer {
	private static final Identifier DEFAULT_CURSOR = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "textures/gui/cursor/default.png");
	private static final double CURSOR_DEPTH = 0.018;
	private static final double DEFAULT_CURSOR_SIZE = 24.0;

	private CursorRenderer() {}

	public static void renderWorldCursor(LevelRenderContext context, PoseStack poseStack, Vec3 localX, Vec3 localY, Vec3 normal, WLCAbstractWindow window) {
		CursorPlacement placement = placementForWorldWindow(window);
		if(placement == null) return;

		Vec3 depth = normal.scale(CURSOR_DEPTH);
		IconSurface cursor = WaylandCraft.instance.bridge.cursorIcon;
		if(cursor != null && cursor.framebuffer != null && cursor.framebuffer.isValid()) {
			WindowFramebuffer framebuffer = cursor.framebuffer;
			double x = placement.x - WaylandCraft.instance.bridge.cursorHotspotX - framebuffer.getXOff();
			double y = placement.y - WaylandCraft.instance.bridge.cursorHotspotY - framebuffer.getYOff();
			renderFramebuffer3D(context, poseStack, localX, localY, depth, framebuffer, x, y, framebuffer.getWidth(), framebuffer.getHeight());
			return;
		}

		if(WaylandCraft.instance.cursorShape == CursorShape.HIDE) return;
		renderTexture3D(context, poseStack, localX, localY, depth, DEFAULT_CURSOR, placement.x, placement.y, DEFAULT_CURSOR_SIZE, DEFAULT_CURSOR_SIZE);
	}

	public static void renderScreenCursor(GuiGraphicsExtractor context, WLCAbstractWindow window, double originX, double originY, double scale, @Nullable WLCSurface surface, double surfaceX, double surfaceY) {
		if(surface == null || !surfaceInTree(window.getSurfaceTree(), surface)) return;

		double pointerX = originX + (surface.xSubpos + surfaceX) * scale;
		double pointerY = originY + (surface.ySubpos + surfaceY) * scale;

		IconSurface cursor = WaylandCraft.instance.bridge.cursorIcon;
		if(cursor != null && cursor.framebuffer != null && cursor.framebuffer.isValid()) {
			WindowFramebuffer framebuffer = cursor.framebuffer;
			double x = pointerX - (WaylandCraft.instance.bridge.cursorHotspotX + framebuffer.getXOff()) * scale;
			double y = pointerY - (WaylandCraft.instance.bridge.cursorHotspotY + framebuffer.getYOff()) * scale;
			double width = framebuffer.getWidth() * scale;
			double height = framebuffer.getHeight() * scale;
			RenderUtils.renderFramebuffer2D(context, framebuffer, (int) Math.round(x), (int) Math.round(y), (int) Math.round(width), (int) Math.round(height));
			return;
		}

		if(WaylandCraft.instance.cursorShape == CursorShape.HIDE) return;
		double size = DEFAULT_CURSOR_SIZE * scale;
		RenderUtils.renderTexture2D(context, DEFAULT_CURSOR, pointerX, pointerY, size, size);
	}

	private static CursorPlacement placementForWorldWindow(WLCAbstractWindow window) {
		WaylandCraft wlc = WaylandCraft.instance;
		if(wlc == null || wlc.bridge == null || window.getSurfaceTree() == null) return null;

		WaylandCraft.PointerCapture capture = wlc.pointerCapture;
		if(capture != null && surfaceInTree(window.getSurfaceTree(), capture.surface)) {
			return new CursorPlacement(capture.surface.xSubpos + capture.x, capture.surface.ySubpos + capture.y);
		}

		DisplayHitResult hovered = wlc.hoveredDisplay;
		if(hovered == null || hovered.target.window != window || hovered.surface == null || hovered.surfaceLocalRelative == null) {
			return null;
		}

		return new CursorPlacement(
				hovered.surface.xSubpos + hovered.surfaceLocalRelative.x,
				hovered.surface.ySubpos + hovered.surfaceLocalRelative.y);
	}

	private static void renderFramebuffer3D(LevelRenderContext context, PoseStack poseStack, Vec3 localX, Vec3 localY, Vec3 depth, WindowFramebuffer framebuffer, double x, double y, double width, double height) {
		Vec3 topLeft = localX.scale(x).add(localY.scale(y)).add(depth);
		Vec3 bottomLeft = localX.scale(x).add(localY.scale(y + height)).add(depth);
		Vec3 bottomRight = localX.scale(x + width).add(localY.scale(y + height)).add(depth);
		Vec3 topRight = localX.scale(x + width).add(localY.scale(y)).add(depth);
		RenderUtils.renderFramebuffer(framebuffer, poseStack, context.submitNodeCollector(), true, topLeft, bottomLeft, bottomRight, topRight);
	}

	private static void renderTexture3D(LevelRenderContext context, PoseStack poseStack, Vec3 localX, Vec3 localY, Vec3 depth, Identifier texture, double x, double y, double width, double height) {
		Vec3 topLeft = localX.scale(x).add(localY.scale(y)).add(depth);
		Vec3 bottomLeft = localX.scale(x).add(localY.scale(y + height)).add(depth);
		Vec3 bottomRight = localX.scale(x + width).add(localY.scale(y + height)).add(depth);
		Vec3 topRight = localX.scale(x + width).add(localY.scale(y)).add(depth);
		RenderUtils.renderTexture(poseStack, context.submitNodeCollector(), texture, true, topLeft, bottomLeft, bottomRight, topRight);
	}

	private static boolean surfaceInTree(WLCSurface root, WLCSurface target) {
		for(WLCSurface surface = root; surface != null; surface = surface.getNextChild()) {
			if(surface == target) return true;
		}
		return false;
	}

	private static record CursorPlacement(double x, double y) {}
}
