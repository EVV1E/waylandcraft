package dev.evvie.waylandcraft.render;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCSurface.SurfaceDamage;
import dev.evvie.waylandcraft.bridge.WLCSurface.ViewportSource;
import dev.evvie.waylandcraft.displays.FramebufferRenderable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class WindowFramebuffer implements FramebufferRenderable {
	
	private static boolean debugDamage = false;
	
	public final WLCSurface surfaceTree;
	private TextureTarget tempTarget = null;
	private TextureTarget target = null;
	private FramebufferTexture texture = null;
	private ResourceLocation location = null;
	
	private int width = 0;
	private int height = 0;
	private int xoff;
	private int yoff;
	
	public WindowFramebuffer(WLCSurface surfaceTree) {
		this.surfaceTree = surfaceTree;
	}
	
	private void updateTarget() {
		int minX = 0;
		int minY = 0;
		int maxX = 0;
		int maxY = 0;
		
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			int sMinX = surface.xSubpos;
			int sMinY = surface.ySubpos;
			int sMaxX = sMinX + surface.width();
			int sMaxY = sMinY + surface.height();
			
			if(sMinX < minX) minX = sMinX;
			if(sMinY < minY) minY = sMinY;
			if(sMaxX > maxX) maxX = sMaxX;
			if(sMaxY > maxY) maxY = sMaxY;
		}
		
		int prevWidth = width;
		int prevHeight = height;
		
		this.xoff = -minX;
		this.yoff = -minY;
		this.width = maxX - minX;
		this.height = maxY - minY;
		
		if(width <= 0 || height <= 0) {
			destroy();
			return;
		}
		
		if(width != prevWidth || height != prevHeight) destroy();
		
		if(tempTarget == null) {
			tempTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		}
		
		if(target == null) {
			target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		}
		
		if(texture == null) registerTexture();
	}
	
	private String name() {
		return "wayland-framebuffer-" + this.hashCode() + "-" + surfaceTree.hashCode();
	}
	
	public void render() {
		updateTarget();
		if(target == null || tempTarget == null) return;
		
		Matrix4f transform = new Matrix4f()
			.translate(-1.0f, -1.0f, 0.0f)
			.scale(2.0f / width, 2.0f / height, 1.0f);
		
		WindowShaders.beginTarget(tempTarget, true);
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			drawSurface(surface, transform, xoff + surface.xSubpos, yoff + surface.ySubpos);
		}
		
		if(debugDamage) drawDebugDamage(transform);
		
		WindowShaders.beginTarget(target, false);
		WindowShaders.drawFullscreen(WindowShaders.unpremultiply, tempTarget.getColorTextureId(), false);
		WindowShaders.endTargets();
	}
	
	private void drawDebugDamage(Matrix4f transform) {
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			int sx = xoff + surface.xSubpos;
			int sy = yoff + surface.ySubpos;
			
			for(SurfaceDamage damage : surface.getDamage()) {
				WindowShaders.drawQuad(WindowShaders.damage, -1, transform, true, 0.0f, sx + damage.x(), sy + damage.y(), damage.width(), damage.height(), 0, 0, 0, 0);
			}
		}
	}
	
	private void drawSurface(WLCSurface surface, Matrix4f transform, float x, float y) {
		BufferTexture buf = surface.getBuffer();
		if(buf == null || buf.getTextureId() < 0) return;
		
		float w = surface.width();
		float h = surface.height();
		
		float crop_x1 = 0.0f;
		float crop_y1 = 0.0f;
		float crop_x2 = 1.0f;
		float crop_y2 = 1.0f;
		
		ViewportSource src = surface.getViewportSource();
		if(src != null) {
			crop_x1 = (float) (src.x() / buf.width);
			crop_y1 = (float) (src.y() / buf.height);
			crop_x2 = (float) ((src.x() + src.width()) / buf.width);
			crop_y2 = (float) ((src.y() + src.height()) / buf.height);
		}
		
		boolean alpha = buf.format != BufferTexture.FORMAT_XRGB8888;
		WindowShaders.drawQuad(WindowShaders.window, buf.getTextureId(), transform, true, alpha ? 0.0f : 1.0f, x, y, w, h, crop_x1, crop_y1, crop_x2, crop_y2);
	}
	
	private void registerTexture() {
		if(target == null) return;
		
		texture = new FramebufferTexture(target.getColorTextureId());
		location = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, name());
		
		Minecraft.getInstance().getTextureManager().register(location, texture);
	}
	
	private void unregisterTexture() {
		Minecraft.getInstance().getTextureManager().release(location);
		texture = null;
		location = null;
	}
	
	public void destroy() {
		if(texture != null) unregisterTexture();
		if(target != null) target.destroyBuffers();
		if(tempTarget != null) tempTarget.destroyBuffers();
		target = null;
		tempTarget = null;
	}
	
	@Override
	public int getWidth() {
		return width;
	}
	
	@Override
	public int getHeight() {
		return height;
	}
	
	@Override
	public int getXOff() {
		return xoff;
	}
	
	@Override
	public int getYOff() {
		return yoff;
	}
	
	public int getTextureId() {
		if(target == null) return -1;
		return target.getColorTextureId();
	}
	
	public ResourceLocation getTextureLocation() {
		return location;
	}
	
	public boolean isValid() {
		return target != null;
	}
	
	// Exposes the target's color texture to the TextureManager without owning it:
	// the GL texture is deleted by destroyBuffers(), not by the texture manager.
	private static class FramebufferTexture extends AbstractTexture {
		
		public FramebufferTexture(int id) {
			this.id = id;
		}
		
		@Override
		public void load(ResourceManager manager) {
		}
		
		// Render types request their own filtering; window framebuffers are always sampled
		// with linear minification and nearest magnification instead
		@Override
		public void setFilter(boolean blur, boolean mipmap) {
			GlStateManager._bindTexture(this.id);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
		}
		
		@Override
		public void releaseId() {
			this.id = -1;
		}
		
		@Override
		public void close() {
		}
		
	}
	
}
