package dev.evvie.waylandcraft.datasync;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.WindowDataPayload;
import dev.evvie.waylandcraft.network.WindowMetadataPayload;
import dev.evvie.waylandcraft.render.RemoteFramebuffer;
import dev.evvie.waylandcraft.utils.TwoBitElementArray;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class WindowDataImporter {
	
	public ArrayList<RemoteFramebuffer> framebuffers = new ArrayList<RemoteFramebuffer>();
	private ArrayDeque<WindowDataPayload> dataPayloads = new ArrayDeque<WindowDataPayload>();
	private ArrayDeque<WindowMetadataPayload> metadataPayloads = new ArrayDeque<WindowMetadataPayload>();
	private boolean reset = false;
	
	public void update() {
		ProfilerFiller profiler = Profiler.get();
		profiler.push("wayland-import");
		
		if(reset) {
			for(RemoteFramebuffer framebuf : framebuffers) {
				framebuf.destroy();
			}
			framebuffers.clear();
			reset = false;
		}
		
		WindowMetadataPayload metadataPayload;
		while((metadataPayload = metadataPayloads.pollLast()) != null) {
			importMetadataPayload(metadataPayload);
		}
		
		WindowDataPayload dataPayload;
		while((dataPayload = dataPayloads.pollLast()) != null) {
			importDataPayload(dataPayload);
		}
		
		profiler.pop();
	}
	
	public void reset() {
		reset = true;
	}
	
	public @Nullable RemoteFramebuffer getFramebuffer(WindowHandle handle) {
		return framebuffers.stream().filter((f) -> f.handle.equals(handle)).findAny().orElse(null);
	}
	
	public void handleWindowMetadataPayload(WindowMetadataPayload payload, ClientPlayNetworking.Context ctx) {
		metadataPayloads.add(payload);
	}
	
	private void importMetadataPayload(WindowMetadataPayload payload) {
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
		dataPayloads.push(payload);
	}
	
	private void importDataPayload(WindowDataPayload payload) {
		RemoteFramebuffer framebuf = getFramebuffer(payload.handle());
		if(framebuf == null) return;
		
		ImagePatch patch = payload.patch();
		
		GpuTexture texture;
		if(patch.format() == ImagePatch.FORMAT_RGBA) {
			System.out.println("IMPORTING RGBA");
			texture = importTextureRGBA(patch.width(), patch.height(), patch.data());
		}
		else if(patch.format() == ImagePatch.FORMAT_RGB) {
			System.out.println("IMPORTING RGB");
			texture = importTextureRGB(patch.width(), patch.height(), patch.data());
		}
		else if(patch.format() == ImagePatch.FORMAT_RGBsA) {
			System.out.println("IMPORTING RGBsA");
			texture = importTextureRGBsA(patch.width(), patch.height(), patch.data());
		}
		else {
			return;
		}
		
		if(texture == null) return;
		
		framebuf.renderPatch(patch.x(), patch.y(), patch.width(), patch.height(), texture);
		texture.close();
	}
	
	private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
	
	private @Nullable GpuTexture importTextureRGBA(int width, int height, byte[] data) {
		if(data.length != width * height * 4) {
			WaylandCraftCommon.LOGGER.error("Received RGBA image patch with incorrect length. width: " + width + ", height: " + height + ", bytes: " + data.length);
			return null;
		}
		
		ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		buf.put(data);
		buf.rewind();
		
		GpuTexture texture = RenderSystem.getDevice().createTexture("imported-wayland-framebuf-" + data.hashCode(), TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, buf, Format.RGBA, 0, 0, 0, 0, width, height);
		
		RenderSystem.queueFencedTask(() -> buf.flip()); // HACK: Keep ByteBuffer alive until it was imported to OpenGL
		
		return texture;
	}
	
	private @Nullable GpuTexture importTextureRGBsA(int width, int height, byte[] data) {
		int alphaByteCount = TwoBitElementArray.bytesForElements(width * height);
		if(data.length != width * height * 3 + alphaByteCount) {
			WaylandCraftCommon.LOGGER.error("Received RGBsA image patch with incorrect length. width: " + width + ", height: " + height + ", bytes: " + data.length);
			return null;
		}
		
		ByteBuffer dataBuf = ByteBuffer.wrap(data);
		byte[] alpha = new byte[alphaByteCount];
		dataBuf.position(width * height * 3);
		dataBuf.get(alpha);
		
		TwoBitElementArray alphaArray = new TwoBitElementArray(width * height, alpha);
		
		// Convert to RGBA, then import to OpenGL
		ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
		for(int i = 0; i < width * height; i++) {
			buf.put(data[i * 3 + 0]);
			buf.put(data[i * 3 + 1]);
			buf.put(data[i * 3 + 2]);
			buf.put((byte) (alphaArray.get(i) * (255 / 3)));
		}
		
		buf.rewind();
		
		GpuTexture texture = RenderSystem.getDevice().createTexture("imported-wayland-framebuf-" + data.hashCode(), TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, buf, Format.RGBA, 0, 0, 0, 0, width, height);
		RenderSystem.queueFencedTask(() -> buf.flip()); // HACK: Keep ByteBuffer alive until it was imported to OpenGL
		
		return texture;
	}
	
	private @Nullable GpuTexture importTextureRGB(int width, int height, byte[] data) {
		if(data.length != width * height * 3) {
			WaylandCraftCommon.LOGGER.error("Received RGB image patch with incorrect length. width: " + width + ", height: " + height + ", bytes: " + data.length);
			return null;
		}
		
		// Convert to RGBA, then import to OpenGL
		ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
		for(int i = 0; i < width * height; i++) {
			buf.put(data[i * 3 + 0]);
			buf.put(data[i * 3 + 1]);
			buf.put(data[i * 3 + 2]);
			buf.put((byte) 0xff);
		}
		
		buf.rewind();
		
		GpuTexture texture = RenderSystem.getDevice().createTexture("imported-wayland-framebuf-" + data.hashCode(), TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, buf, Format.RGBA, 0, 0, 0, 0, width, height);
		RenderSystem.queueFencedTask(() -> buf.flip()); // HACK: Keep ByteBuffer alive until it was imported to OpenGL
		
		return texture;
	}
	
}
