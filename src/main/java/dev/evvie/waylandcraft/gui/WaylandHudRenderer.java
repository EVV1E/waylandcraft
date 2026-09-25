package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.Calendar;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraft.KeyboardCaptureMode;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.IconSurface;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.render.RenderUtils;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public class WaylandHudRenderer {
	
	private WaylandCraft wlc;
	private static final ResourceLocation TIME_DATE = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "time-date");
	private static final ResourceLocation APP_LIST = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "app-list");
	private static final ResourceLocation PINNED_TOPLEVEL = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pinned-toplevel");
	private static final ResourceLocation DND_ICON = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "dnd-icon");
	private static final ResourceLocation SHARING = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "sharing");
	
	public WaylandHudRenderer(WaylandCraft wlc) {
		this.wlc = wlc;
	}
	
	public void register(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, TIME_DATE, this::renderTimeDate);
		event.registerAbove(TIME_DATE, APP_LIST, this::renderAppList);
		event.registerAbove(APP_LIST, PINNED_TOPLEVEL, this::renderPinnedToplevel);
		event.registerAbove(PINNED_TOPLEVEL, DND_ICON, this::renderDNDIcon);
		event.registerAbove(DND_ICON, SHARING, this::renderSharing);
	}
	
	private void renderAppList(GuiGraphics context, DeltaTracker deltaTracker) {
		Font font = Minecraft.getInstance().font;
		int yoff = 30;
		int ystep = font.lineHeight + 2;
		
		if(WaylandCraft.instance.keyboardCaptureMode == KeyboardCaptureMode.CAPTURE) {
			String text = "KEYBOARD CAPTURED [PRESS ESCAPE]";
			context.drawString(font, text, context.guiWidth() - font.width(text) - 10, yoff, Color.red.getRGB(), true);
			yoff += ystep;
		}
		else if(WaylandCraft.instance.keyboardCaptureMode == KeyboardCaptureMode.HARD_CAPTURE) {
			String text = "KEYBOARD CAPTURED [PRESS ALT+Q]";
			context.drawString(font, text, context.guiWidth() - font.width(text) - 10, yoff, Color.red.getRGB(), true);
			yoff += ystep;
		}
		
		for(WLCToplevel toplevel : WaylandCraft.instance.bridge.getMappedToplevels()) {
			String appID = toplevel.appID;
			DesktopEntry entry = wlc.xdgManager.forAppId(appID);
			
			String name = "<unknown app>";
			if(appID != null) name = appID;
			if(entry != null && entry.name != null) name = entry.name;
			
			Style style = Style.EMPTY;
			Color color = Color.white;
			
			if(!wlc.hasDisplayFor(toplevel)) {
				color = Color.lightGray;
			}
			if(toplevel == wlc.bridge.getMostRecentFocus()) {
				style = style.applyFormat(ChatFormatting.UNDERLINE);
			}
			
			int x = context.guiWidth() - font.width(name) - 10;
			context.drawString(font, Component.literal(name).withStyle(style), x, yoff, color.getRGB(), true);
			
			if(entry != null) {
				ResourceLocation icon = entry.getIcon();
				int iconX = x - font.lineHeight - 2;
				int iconY = yoff;
				int iconSize = font.lineHeight;
				if(icon != null) RenderUtils.blitFull(context, icon, iconX, iconY, iconX + iconSize, iconY + iconSize);
			}
			
			yoff += ystep;
		}
	}
	
	private void renderPinnedToplevel(GuiGraphics context, DeltaTracker deltaTracker) {
		int guiScale = (int) Minecraft.getInstance().getWindow().getGuiScale();
		
		if(wlc.pinnedToplevel != null && !wlc.pinnedToplevel.isAlive()) wlc.pinnedToplevel = null;
		if(wlc.pinnedToplevel != null) {
			WindowFramebuffer buf = wlc.pinnedToplevel.framebuffer;
			if(buf == null) return;
			
			SurfaceGeometry geometry = wlc.pinnedToplevel.geometry;
			
			int x = -buf.getXOff() - geometry.x();
			int y = -buf.getYOff() - geometry.y();
			int w = buf.getWidth();
			int h = buf.getHeight();
			
			PoseStack stack = context.pose();
			stack.pushPose();
			stack.scale(1.0f / guiScale * 0.5f, 1.0f / guiScale * 0.5f, 1.0f);
			RenderUtils.renderFramebuffer2D(context, buf, x, y, w, h);
			stack.popPose();
		}
	}
	
	private void renderDNDIcon(GuiGraphics context, DeltaTracker tracker) {
		int guiScale = (int) Minecraft.getInstance().getWindow().getGuiScale();
		
		IconSurface dndIcon = wlc.bridge.dndIcon;
		if(dndIcon != null && dndIcon.framebuffer != null) {
			WindowFramebuffer buf = dndIcon.framebuffer;
			
			int x = -buf.getXOff();
			int y = -buf.getYOff();
			int w = buf.getWidth();
			int h = buf.getHeight();
			
			PoseStack stack = context.pose();
			stack.pushPose();
			stack.translate(context.guiWidth() / 2, context.guiHeight() / 2, 0.0f);
			stack.scale(1.0f / guiScale, 1.0f / guiScale, 1.0f);
			RenderUtils.renderFramebuffer2D(context, buf, x, y, w, h);
			stack.popPose();
		}
	}
	
	// Always-visible list of windows shared with other players
	private void renderSharing(GuiGraphics context, DeltaTracker deltaTracker) {
		List<String> titles = wlc.sharingOwner.sharedTitles();
		if(titles.isEmpty()) return;
		
		Font font = Minecraft.getInstance().font;
		int maxWidth = context.guiWidth() / 3;
		int y = 4 + font.lineHeight;
		for(String title : titles) {
			String text = "\u25CF Sharing: " + title;
			if(font.width(text) > maxWidth) text = font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
			context.drawString(font, text, context.guiWidth() - font.width(text) - 2, y, 0xFFFF5555, true);
			y += font.lineHeight + 1;
		}
	}
	
	private void renderTimeDate(GuiGraphics context, DeltaTracker deltaTracker) {
		Font font = Minecraft.getInstance().font;
		String datetime = String.format("%1$tF %1$tR", Calendar.getInstance());
		
		context.drawString(font, datetime, context.guiWidth() - font.width(datetime) - 2, 2, Color.white.getRGB(), true);
	}
	
}
