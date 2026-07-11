package dev.evvie.waylandcraft.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public class RemoteFramebuffer {
	
	public final WindowHandle handle;
	
	public final int width;
	public final int height;
	public int xoff;
	public int yoff;
	
	private final TextureTarget target;
	private FramebufferTexture texture = null;
	private Identifier location = null;
	
	public RemoteFramebuffer(WindowHandle handle, int width, int height, int xoff, int yoff) {
		this.handle = handle;
		this.width = width;
		this.height = height;
		this.xoff = xoff;
		this.yoff = yoff;
		
		target = new TextureTarget(getName(), width, height, false);
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0x00000000);
		
		registerTexture();
	}
	
	public String getName() {
		return "remote-framebuffer-" + this.hashCode();
	}
	
	public void renderPatch(int x, int y, int w, int h, GpuTexture texture) {
//		System.out.println("renderPatch(x=" + x + ", y=" + y + ", width=" + w + ", height=" + h + ")");
		
		if(x < 0 || y < 0 || x + w > width || y + h > height) return;
		
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(texture, target.getColorTexture(), 0, x, y, 0, 0, w, h);
	}
	
	public GpuTextureView getTextureView() {
		return target.getColorTextureView();
	}
	
	public Identifier getTextureLocation() {
		return location;
	}
	
	public void destroy() {
		unregisterTexture();
		target.destroyBuffers();
	}
	
	private void registerTexture() {
		texture = new FramebufferTexture(getTextureView());
		location = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, getName());
		
		Minecraft.getInstance().getTextureManager().register(location, texture);
	}
	
	private void unregisterTexture() {
		TextureManager manager = Minecraft.getInstance().getTextureManager();
		manager.register(location, manager.getTexture(MissingTextureAtlasSprite.getLocation()));
		texture = null;
	}
	
	private static class FramebufferTexture extends AbstractTexture {
		
		public FramebufferTexture(GpuTextureView textureView) {
			this.textureView = textureView;
			this.texture = textureView.texture();
			this.sampler = RenderUtils.WINDOW_SAMPLER.get();
		}
		
		@Override
		public void close() {
		}
		
	}
	
}
