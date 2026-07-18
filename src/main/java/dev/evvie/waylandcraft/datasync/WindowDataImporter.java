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
	private ArrayDeque<WindowMetadataPayload> metadataPayloads = new ArrayDeque<WindowMetadataPayload>();
	private ArrayDeque<WindowDataPayload> dataPayloads = new ArrayDeque<WindowDataPayload>();
	private ArrayDeque<DrawablePatch> drawablePatches = new ArrayDeque<DrawablePatch>();
	
	private Thread workerThread;
	private boolean reset = false;
	
	public WindowDataImporter() {
		workerThread = new Thread(() -> {while(true) {this.doWork();}});
		workerThread.setDaemon(true);
		workerThread.start();
	}
	
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
		
		// Render at most one patch per frame
		renderSinglePatch();
		
		profiler.pop();
	}
	
	public void reset() {
		reset = true;
	}
	
	private void renderSinglePatch() {
		DrawablePatch patch;
		synchronized(drawablePatches) {
			patch = drawablePatches.pollLast();
			if(patch == null) return;
		}
		
		renderPatch(patch);
	}
	
	private void renderPatch(DrawablePatch drawable) {
		RemoteFramebuffer framebuf = getFramebuffer(drawable.handle());
		if(framebuf == null) return;
		
		ProfilerFiller profiler = Profiler.get();
		profiler.push("upload-patch");
		
		PatchRGBA patch = drawable.inner();
		GpuTexture texture = uploadPatch(patch);
		
		profiler.popPush("render-patch");
		
		framebuf.renderPatch(patch.x(), patch.y(), patch.width(), patch.height(), texture);
		texture.close();
		
		profiler.pop();
	}
	
	private void doWork() {
		WindowDataPayload payload;
		synchronized(dataPayloads) {
			while((payload = dataPayloads.pollLast()) == null) {
				try {
					dataPayloads.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
		
		ImagePatch patch = payload.patch();
		PatchRGBA converted;
		if(patch.format() == ImagePatch.FORMAT_RGBA) {
			System.out.println("IMPORTING RGBA");
			converted = importPatchRGBA(patch);
		}
		else if(patch.format() == ImagePatch.FORMAT_RGB) {
			System.out.println("IMPORTING RGB");
			converted = importPatchRGB(patch);
		}
		else if(patch.format() == ImagePatch.FORMAT_RGBsA) {
			System.out.println("IMPORTING RGBsA");
			converted = importPatchRGBsA(patch);
		}
		else {
			return;
		}
		
		if(converted == null) return;
		
		synchronized(drawablePatches) {
			drawablePatches.push(new DrawablePatch(payload.handle(), converted));
		}
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
		synchronized(dataPayloads) {
			dataPayloads.push(payload);
			dataPayloads.notify();
		}
	}
	
	private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
	
	private GpuTexture uploadPatch(PatchRGBA patch) {
		GpuTexture texture = RenderSystem.getDevice().createTexture("imported-wayland-framebuf-" + patch.hashCode(), TEXTURE_USAGE, TextureFormat.RGBA8, patch.width(), patch.height(), 1, 1);
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, patch.data(), Format.RGBA, 0, 0, 0, 0, patch.width(), patch.height());
		
		RenderSystem.queueFencedTask(() -> patch.data().flip()); // HACK: Keep ByteBuffer alive until it was imported to OpenGL
		return texture;
	}
	
	private @Nullable PatchRGBA importPatchRGBA(ImagePatch patch) {
		int width = patch.width();
		int height = patch.height();
		byte[] data = patch.data();
		
		if(data.length != width * height * 4) {
			WaylandCraftCommon.LOGGER.error("Received RGBA image patch with incorrect length. width: " + width + ", height: " + height + ", bytes: " + data.length);
			return null;
		}
		
		ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
		buf.put(data);
		buf.rewind();
		
		return new PatchRGBA(patch, buf);
	}
	
	private @Nullable PatchRGBA importPatchRGBsA(ImagePatch patch) {
		int width = patch.width();
		int height = patch.height();
		byte[] data = patch.data();
		
		int alphaByteCount = TwoBitElementArray.bytesForElements(width * height);
		if(data.length != width * height * 3 + alphaByteCount) {
			WaylandCraftCommon.LOGGER.error("Received RGBsA image patch with incorrect length. width: " + width + ", height: " + height + ", bytes: " + data.length);
			return null;
		}
		
		ProfilerFiller profiler = Profiler.get();
		profiler.push("data-processing-rgbsa");
		
		int alphaOffset = width * height * 3; // After RGB bytes
		byte[] alpha = new byte[alphaByteCount];
		System.arraycopy(data, alphaOffset, alpha, 0, alphaByteCount);
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
		profiler.pop();
		
		return new PatchRGBA(patch, buf);
	}
	
	private @Nullable PatchRGBA importPatchRGB(ImagePatch patch) {
		int width = patch.width();
		int height = patch.height();
		byte[] data = patch.data();
		
		if(data.length != width * height * 3) {
			WaylandCraftCommon.LOGGER.error("Received RGB image patch with incorrect length. width: " + width + ", height: " + height + ", bytes: " + data.length);
			return null;
		}
		
		ProfilerFiller profiler = Profiler.get();
		profiler.push("data-processing-rgb");
		
		// Convert to RGBA, then import to OpenGL
		ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
		for(int i = 0; i < width * height; i++) {
			buf.put(data[i * 3 + 0]);
			buf.put(data[i * 3 + 1]);
			buf.put(data[i * 3 + 2]);
			buf.put((byte) 0xff);
		}
		
		buf.rewind();
		profiler.pop();
		
		return new PatchRGBA(patch, buf);
	}
	
	private static record PatchRGBA(int x, int y, int width, int height, ByteBuffer data) {
		
		public PatchRGBA(ImagePatch patch, ByteBuffer data) {
			this(patch.x(), patch.y(), patch.width(), patch.height(), data);
		}
		
	}
	
	private static record DrawablePatch(WindowHandle handle, PatchRGBA inner) {
	}
	
}
