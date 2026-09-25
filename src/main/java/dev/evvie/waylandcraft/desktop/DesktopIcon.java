package dev.evvie.waylandcraft.desktop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.commons.codec.digest.DigestUtils;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

public class DesktopIcon {
	
	public final String path;
	
	private WaylandCraft wlc;
	
	private NativeImage image = null;
	private DynamicTexture texture = null;
	private final ResourceLocation identifier;
	
	public DesktopIcon(String appId, String path) {
		this.path = path;
		this.identifier = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "icon_" + DigestUtils.sha1Hex(appId));
		this.wlc = WaylandCraft.instance;
	}
	
	public synchronized void preload() {
		if(image != null) return; // image already preloaded
		if(path == null) return;
		
		File file = new File(path);
		
		/* These "file type checks" are valid because according to the Icon Theme Specification
		 * the extension has to be one of ".png", ".xpm" and ".svg" (lowercase) and the extension
		 * signals what type of file we should expect.
		 */
		
		if(getExtension(file).equals("png")) {
			try {
				FileInputStream stream = new FileInputStream(file);
				this.image = NativeImage.read(stream);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		else if(getExtension(file).equals("svg")) {
			final int width = 128;
			final int height = 128;
			
			ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
			long addr = MemoryUtil.memAddress(buf);
			
			if(wlc.bridge.renderSVG(file, width, height, addr)) {
				// Copy the RGBA bytes into an image that owns its memory.
				// setPixelRGBA takes the pixel as a little-endian RGBA int.
				buf.order(ByteOrder.LITTLE_ENDIAN);
				NativeImage svgImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
				for(int y = 0; y < height; y++) {
					for(int x = 0; x < width; x++) {
						svgImage.setPixelRGBA(x, y, buf.getInt((y * width + x) * 4));
					}
				}
				this.image = svgImage;
			}
		}
	}
	
	public void upload() {
		if(texture != null) return; // image already uploaded
		
		if(image == null) {
			// When upload is called before preload, that necessary step is completed first.
			preload();
		}
		if(image == null) return;
		
		// DynamicTexture uploads the image and takes ownership of it
		texture = new DynamicTexture(image);
		
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		textureManager.register(identifier, texture);
	}
	
	public ResourceLocation getTextureLocation() {
		this.upload();
		if(texture == null) return null;
		return identifier;
	}
	
	private String getExtension(File file) {
		String path = file.getAbsolutePath();
		int idx = path.lastIndexOf('.');
		if(idx < 0 || idx >= path.length() - 1) return "";
		
		return path.substring(idx + 1);
	}
	
}
