package dev.evvie.waylandcraft.sharing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.pipeline.TextureTarget;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.network.WaylandCraftNetworking;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import dev.evvie.waylandcraft.render.WindowShaders;
import dev.evvie.waylandcraft.sharing.SharingNetworking.AudioChunkPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.DemandPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.ShareStatePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.VideoChunkPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/* Owner side of window sharing. The player explicitly toggles sharing per window
 * (keybind, focused window). Shared windows are only captured while the server
 * reports demand: video while someone can see the window, audio while someone
 * is near a frame showing it.
 */
public class SharingOwner {

	private static final long FRAME_INTERVAL_MILLIS = 250;
	private static final int MAX_DIMENSION = 480;
	private static final float JPEG_QUALITY = 0.7f;

	private final WaylandCraft wlc;
	private final Map<Long, SharedWindow> shared = new HashMap<>();
	private final ExecutorService encoder = Executors.newSingleThreadExecutor((r) -> {
		Thread t = new Thread(r, "WaylandCraft sharing encoder");
		t.setDaemon(true);
		return t;
	});

	private TextureTarget captureTarget = null;

	private class SharedWindow {
		final WLCToplevel toplevel;
		boolean videoDemand = false;
		boolean audioDemand = false;
		long lastFrameTime = 0;
		int frameId = 0;
		boolean encoding = false;
		byte[] lastJpeg = null;
		AudioCapture audio = null;

		SharedWindow(WLCToplevel toplevel) {
			this.toplevel = toplevel;
		}
	}

	public SharingOwner(WaylandCraft wlc) {
		this.wlc = wlc;
	}

	public int sharedCount() {
		return shared.size();
	}

	public boolean isShared(WLCToplevel toplevel) {
		return shared.containsKey(toplevel.getHandle());
	}

	// Toggles sharing of the most recently focused window
	public void toggleFocused() {
		if(wlc.bridge == null) return;
		WLCToplevel toplevel = wlc.bridge.getMostRecentFocus();
		if(toplevel == null) {
			message("No window focused to share");
			return;
		}

		String name = toplevel.title != null ? toplevel.title : "window";
		if(shared.containsKey(toplevel.getHandle())) {
			stop(toplevel.getHandle());
			message("Stopped sharing " + name);
		}
		else {
			shared.put(toplevel.getHandle(), new SharedWindow(toplevel));
			WaylandCraftNetworking.sendToServer(new ShareStatePayload(toplevel.getHandle(), true));
			message("Sharing " + name + " (video and audio) with players who see it in an item frame");
		}
	}

	public void onDemand(DemandPayload payload) {
		SharedWindow window = shared.get(payload.handle());
		if(window == null) return;
		window.videoDemand = payload.video();
		window.audioDemand = payload.audio();
	}

	public void reset() {
		for(Long handle : shared.keySet().toArray(Long[]::new)) stop(handle);
	}

	// Client tick: forget closed windows and start / stop audio capture with demand
	public void tick() {
		Iterator<Map.Entry<Long, SharedWindow>> it = shared.entrySet().iterator();
		while(it.hasNext()) {
			SharedWindow window = it.next().getValue();
			if(!window.toplevel.isAlive()) {
				if(window.audio != null) window.audio.stop();
				WaylandCraftNetworking.sendToServer(new ShareStatePayload(window.toplevel.getHandle(), false));
				it.remove();
				continue;
			}

			if(window.audioDemand) {
				if(window.audio == null) {
					long handle = window.toplevel.getHandle();
					window.audio = new AudioCapture(wlc.bridge.getToplevelPID(window.toplevel), (pcm) ->
						Minecraft.getInstance().execute(() -> WaylandCraftNetworking.sendToServer(new AudioChunkPayload(handle, pcm))));
				}
				window.audio.update();
			}
			else if(window.audio != null) {
				window.audio.stop();
				window.audio = null;
			}
		}
	}

	// Render thread, after the bridge rendered window framebuffers
	public void captureFrames() {
		long now = System.currentTimeMillis();
		for(SharedWindow window : shared.values()) {
			if(!window.videoDemand || window.encoding) continue;
			if(now - window.lastFrameTime < FRAME_INTERVAL_MILLIS) continue;

			WindowFramebuffer framebuffer = window.toplevel.framebuffer;
			if(framebuffer == null || !framebuffer.isValid()) continue;

			window.lastFrameTime = now;
			capture(window, framebuffer);
		}
	}

	private void capture(SharedWindow window, WindowFramebuffer framebuffer) {
		// Downscale on the GPU, then read back the small image
		float scale = Math.min(1.0f, (float) MAX_DIMENSION / Math.max(framebuffer.getWidth(), framebuffer.getHeight()));
		int width = Math.max(1, Math.round(framebuffer.getWidth() * scale));
		int height = Math.max(1, Math.round(framebuffer.getHeight() * scale));

		if(captureTarget == null) {
			captureTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		}
		else if(captureTarget.width != width || captureTarget.height != height) {
			captureTarget.resize(width, height, Minecraft.ON_OSX);
		}

		WindowShaders.beginTarget(captureTarget, true);
		// The framebuffer holds straight alpha; alpha blend 1 makes the copy opaque for JPEG
		WindowShaders.drawQuad(WindowShaders.window, framebuffer.getTextureId(), new org.joml.Matrix4f(), false, 1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 0.0f, 0.0f, 1.0f, 1.0f);

		ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		WindowShaders.endTargets();

		window.encoding = true;
		long handle = window.toplevel.getHandle();
		int frameId = ++window.frameId;
		encoder.execute(() -> {
			try {
				byte[] jpeg = encodeJpeg(pixels, width, height);
				Minecraft.getInstance().execute(() -> {
					window.encoding = false;
					if(!shared.containsKey(handle) || Arrays.equals(jpeg, window.lastJpeg)) return;
					window.lastJpeg = jpeg;
					sendFrame(handle, frameId, jpeg);
				});
			} catch(IOException e) {
				WaylandCraftCommon.LOGGER.error("Failed to encode shared window frame", e);
				Minecraft.getInstance().execute(() -> window.encoding = false);
			} finally {
				MemoryUtil.memFree(pixels);
			}
		});
	}

	// glReadPixels returns the target's row 0 first, which holds the window's top row
	private static byte[] encodeJpeg(ByteBuffer pixels, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int[] row = new int[width];
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				int i = (y * width + x) * 4;
				int r = pixels.get(i) & 0xff;
				int g = pixels.get(i + 1) & 0xff;
				int b = pixels.get(i + 2) & 0xff;
				row[x] = r << 16 | g << 8 | b;
			}
			image.setRGB(0, y, width, 1, row, 0, width);
		}

		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(JPEG_QUALITY);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try(MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
			writer.setOutput(stream);
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	private static void sendFrame(long handle, int frameId, byte[] jpeg) {
		int chunkSize = SharingNetworking.MAX_CHUNK_BYTES;
		int count = (jpeg.length + chunkSize - 1) / chunkSize;
		if(count > SharingNetworking.MAX_CHUNKS_PER_FRAME) return; // Frame too large, skip it

		for(int i = 0; i < count; i++) {
			byte[] data = Arrays.copyOfRange(jpeg, i * chunkSize, Math.min(jpeg.length, (i + 1) * chunkSize));
			WaylandCraftNetworking.sendToServer(new VideoChunkPayload(handle, frameId, i, count, data));
		}
	}

	private void stop(long handle) {
		SharedWindow window = shared.remove(handle);
		if(window == null) return;
		if(window.audio != null) window.audio.stop();
		WaylandCraftNetworking.sendToServer(new ShareStatePayload(handle, false));
	}

	private static void message(String text) {
		Minecraft.getInstance().gui.getChat().addMessage(Component.literal("[WaylandCraft] " + text));
	}

}
