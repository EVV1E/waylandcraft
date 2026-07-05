package dev.evvie.waylandcraft.datasync;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.WindowDataPayload;
import dev.evvie.waylandcraft.network.WindowMetadataPayload;
import dev.evvie.waylandcraft.render.RemoteFramebuffer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.ARGB;

public class WindowDataImporter {
	
	public ArrayList<RemoteFramebuffer> framebuffers = new ArrayList<RemoteFramebuffer>();
	private boolean reset = false;
	
	public void update() {
		if(reset) {
			for(RemoteFramebuffer framebuf : framebuffers) {
				framebuf.destroy();
			}
			framebuffers.clear();
			reset = false;
		}
	}
	
	public void reset() {
		reset = true;
	}
	
	public @Nullable RemoteFramebuffer getFramebuffer(WindowHandle handle) {
		return framebuffers.stream().filter((f) -> f.handle.equals(handle)).findAny().orElse(null);
	}
	
	public void handleWindowMetadataPayload(WindowMetadataPayload payload, ClientPlayNetworking.Context ctx) {
		RemoteFramebuffer framebuf = getFramebuffer(payload.handle());
		if(payload.removed()) {
			System.out.println("GOT REMOVE");
			if(framebuf != null) {
				framebuffers.remove(framebuf);
				framebuf.destroy();
			}
			return;
		}
		
		System.out.println("GOT METADATA");
		if(framebuf != null && (framebuf.width != payload.width() || framebuf.height != payload.height())) {
			framebuf.destroy();
			framebuffers.remove(framebuf);
			framebuf = null;
		}
		
		if(framebuf == null) {
			framebuf = new RemoteFramebuffer(payload.handle(), payload.width(), payload.height(), payload.xoff(), payload.yoff());
			framebuffers.add(framebuf);
		}
		else {
			framebuf.xoff = payload.xoff();
			framebuf.yoff = payload.yoff();
		}
	}
	
	public void handleWindowDataPayload(WindowDataPayload payload, ClientPlayNetworking.Context ctx) {
		System.out.println("GOT DATA");
		RemoteFramebuffer framebuf = getFramebuffer(payload.handle());
		if(framebuf == null) return;
		
		GpuTexture texture = importTextureARGB(payload.width(), payload.height(), payload.data());
		framebuf.renderPatch(payload.x(), payload.y(), payload.width(), payload.height(), texture);
		texture.close();
	}
	
	private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
	
	private GpuTexture importTextureARGB(int width, int height, byte[] data) {
		ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
//		buf.put(data);
		int color = ARGB.color(1.0f, (new Random().nextInt() & 0xffffff));
		for(int i = 0; i < width * height; i++) {
			buf.put((byte) ARGB.red(color));
			buf.put((byte) ARGB.green(color));
			buf.put((byte) ARGB.blue(color));
			buf.put((byte) ARGB.alpha(color));
		}
		buf.rewind();
		
		GpuTexture texture = RenderSystem.getDevice().createTexture("imported-wayland-framebuf-" + data.hashCode(), TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, buf, Format.RGBA, 0, 0, 0, 0, width, height);
		
		return texture;
	}
	
}
