package dev.evvie.waylandcraft.sharing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.pipeline.TextureTarget;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.displays.WindowDisplay;
import dev.evvie.waylandcraft.network.WaylandCraftNetworking;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import dev.evvie.waylandcraft.render.WindowShaders;
import dev.evvie.waylandcraft.sharing.SharingNetworking.AudioChunkPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.DemandPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.DisplayPosePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.ShareStatePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.VideoChunkPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowPose;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/* Owner side of window sharing. The player explicitly toggles sharing per window
 * (keybind, focused window). Shared windows are only captured while the server
 * reports demand: video while someone can see the window, audio while someone
 * is near it.
 *
 * Video is tuned for both moving content (videos, games) and text (terminals):
 * while the window keeps changing, frames are sent as JPEG at a moderate size and up
 * to MOTION_FPS; once it has been still for STILL_DELAY_MILLIS, one lossless PNG at
 * a higher resolution follows, so text becomes crisp. Nothing is captured while the
 * window content doesn't change.
 */
public class SharingOwner {

	private static final int MOTION_FPS = 15;
	private static final int MOTION_MAX_DIMENSION = 854;
	private static final float MOTION_JPEG_QUALITY = 0.7f;

	private static final long STILL_DELAY_MILLIS = 600;
	private static final int STILL_MAX_DIMENSION = 1600;

	private static final long POSE_RESEND_MILLIS = 2000;

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
		// Wayland client pid, read on the main thread (the compositor is not thread-safe)
		final int clientPid;
		boolean videoDemand = false;
		boolean audioDemand = false;

		long lastFrameTime = 0;
		long lastChangeTime = 0;
		// Content version of the last frame sent, and whether it was the lossless still
		long sentVersion = -1;
		boolean stillSent = false;
		int frameId = 0;
		boolean encoding = false;

		AudioCapture audio = null;

		WindowPose sentPose = null;
		boolean poseSent = false;
		long lastPoseTime = 0;

		SharedWindow(WLCToplevel toplevel, int clientPid) {
			this.toplevel = toplevel;
			this.clientPid = clientPid;
		}
	}

	public SharingOwner(WaylandCraft wlc) {
		this.wlc = wlc;
	}

	public int sharedCount() {
		return shared.size();
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
			shared.put(toplevel.getHandle(), new SharedWindow(toplevel, wlc.bridge.getToplevelPID(toplevel)));
			WaylandCraftNetworking.sendToServer(new ShareStatePayload(toplevel.getHandle(), true));
			message("Sharing " + name + " (video and audio). Other players see it in item frames or where you place it.");
		}
	}

	public void onDemand(DemandPayload payload) {
		SharedWindow window = shared.get(payload.handle());
		if(window == null) return;
		if(payload.video() && !window.videoDemand) {
			// New viewers need a full frame
			window.sentVersion = -1;
			window.stillSent = false;
		}
		window.videoDemand = payload.video();
		window.audioDemand = payload.audio();
	}

	public void reset() {
		for(Long handle : shared.keySet().toArray(Long[]::new)) stop(handle);
	}

	// Client tick: forget closed windows, start / stop audio capture with demand, sync floating poses
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

			updateAudio(window);
			updatePose(window);
		}
	}

	private void updateAudio(SharedWindow window) {
		if(!window.audioDemand) {
			if(window.audio != null) window.audio.stop();
			window.audio = null;
			return;
		}

		if(window.audio == null) {
			long handle = window.toplevel.getHandle();
			String x11Display = wlc.x11Display;
			window.audio = new AudioCapture(() -> AudioCapture.resolveX11Pid(window.clientPid, x11Display, window.toplevel.title), (opus) ->
				Minecraft.getInstance().execute(() -> WaylandCraftNetworking.sendToServer(new AudioChunkPayload(handle, opus))));
		}
		window.audio.update();
	}

	// Free-floating displays of shared windows are placed for other players too
	private void updatePose(SharedWindow window) {
		WindowDisplay display = wlc.getDisplay(window.toplevel);
		WindowFramebuffer framebuffer = window.toplevel.framebuffer;
		WindowPose pose = null;
		if(display != null && display.isValid() && framebuffer != null && framebuffer.isValid()) {
			// The shared image is the whole framebuffer (including client-side decorations
			// outside the window geometry), so describe that rectangle
			Vec3 localX = display.localX();
			Vec3 localY = display.localY();
			Vec3 rectOrigin = display.origin()
				.add(localX.scale(-framebuffer.getXOff() - window.toplevel.geometry.x()))
				.add(localY.scale(-framebuffer.getYOff() - window.toplevel.geometry.y()));
			Vec3 center = rectOrigin.add(localX.scale(framebuffer.getWidth() / 2.0)).add(localY.scale(framebuffer.getHeight() / 2.0));
			pose = new WindowPose(center, display.normal(), display.down(),
				(float) localX.length() * framebuffer.getWidth(), (float) localY.length() * framebuffer.getHeight());
		}

		long now = System.currentTimeMillis();
		boolean changed = !window.poseSent || !Objects.equals(pose, window.sentPose);
		boolean resend = pose != null && now - window.lastPoseTime > POSE_RESEND_MILLIS;
		if(!changed && !resend) return;

		window.sentPose = pose;
		window.poseSent = true;
		window.lastPoseTime = now;
		WaylandCraftNetworking.sendToServer(new DisplayPosePayload(window.toplevel.getHandle(), pose));
	}

	// Render thread, after the bridge rendered window framebuffers
	public void captureFrames() {
		long now = System.currentTimeMillis();
		for(SharedWindow window : shared.values()) {
			WindowFramebuffer framebuffer = window.toplevel.framebuffer;
			if(framebuffer == null || !framebuffer.isValid()) continue;

			long version = framebuffer.getContentVersion();

			if(!window.videoDemand || window.encoding) continue;

			if(version != window.sentVersion) {
				// Content changed: send a motion frame, rate limited
				if(now - window.lastFrameTime < 1000 / MOTION_FPS) continue;
				window.lastFrameTime = now;
				window.lastChangeTime = now;
				window.sentVersion = version;
				window.stillSent = false;
				capture(window, framebuffer, false);
			}
			else if(!window.stillSent && now - window.lastChangeTime >= STILL_DELAY_MILLIS) {
				// Unchanged for a while: follow up with one sharp lossless frame
				window.stillSent = true;
				capture(window, framebuffer, true);
			}
		}
	}

	private void capture(SharedWindow window, WindowFramebuffer framebuffer, boolean still) {
		// Downscale on the GPU, then read back the small image
		int maxDimension = still ? STILL_MAX_DIMENSION : MOTION_MAX_DIMENSION;
		float scale = Math.min(1.0f, (float) maxDimension / Math.max(framebuffer.getWidth(), framebuffer.getHeight()));
		int width = Math.max(1, Math.round(framebuffer.getWidth() * scale));
		int height = Math.max(1, Math.round(framebuffer.getHeight() * scale));

		if(captureTarget == null) {
			captureTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		}
		else if(captureTarget.width != width || captureTarget.height != height) {
			captureTarget.resize(width, height, Minecraft.ON_OSX);
		}

		WindowShaders.beginTarget(captureTarget, true);
		// The framebuffer holds straight alpha; alpha blend 1 makes the copy opaque
		WindowShaders.drawQuad(WindowShaders.window, framebuffer.getTextureId(), new Matrix4f(), false, 1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 0.0f, 0.0f, 1.0f, 1.0f);

		ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		WindowShaders.endTargets();

		window.encoding = true;
		long handle = window.toplevel.getHandle();
		int frameId = ++window.frameId;
		encoder.execute(() -> {
			try {
				byte[] encoded = encode(pixels, width, height, still);
				Minecraft.getInstance().execute(() -> {
					window.encoding = false;
					if(shared.containsKey(handle)) sendFrame(handle, frameId, encoded);
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
	private static byte[] encode(ByteBuffer pixels, int width, int height, boolean lossless) throws IOException {
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

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if(lossless) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		}

		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(MOTION_JPEG_QUALITY);
		try(MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
			writer.setOutput(stream);
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	private static void sendFrame(long handle, int frameId, byte[] encoded) {
		int chunkSize = SharingNetworking.MAX_CHUNK_BYTES;
		int count = (encoded.length + chunkSize - 1) / chunkSize;
		if(count > SharingNetworking.MAX_CHUNKS_PER_FRAME) return; // Frame too large, skip it

		for(int i = 0; i < count; i++) {
			byte[] data = Arrays.copyOfRange(encoded, i * chunkSize, Math.min(encoded.length, (i + 1) * chunkSize));
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
