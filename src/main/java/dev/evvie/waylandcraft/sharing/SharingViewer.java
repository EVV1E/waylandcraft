package dev.evvie.waylandcraft.sharing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.network.WaylandCraftNetworking;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamAudioPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamEndPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamVideoPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WatchPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/* Viewer side of window sharing: other players' shared windows shown in item frames.
 *
 * Video is demand-driven. Every few ticks the viewer checks which frames with other
 * players' windows are in view (frustum) and in line of sight (no blocks in between),
 * and tells the server; only those streams are sent. Audio is sent by the server to
 * everyone in range regardless of line of sight, and plays positionally at the frame.
 */
public class SharingViewer {

	private static final double WATCH_RANGE = 64.0;
	private static final int WATCH_INTERVAL_TICKS = 5;

	private final Map<WindowKey, RemoteWindow> windows = new HashMap<>();
	private final ExecutorService decoder = Executors.newSingleThreadExecutor((r) -> {
		Thread t = new Thread(r, "WaylandCraft sharing decoder");
		t.setDaemon(true);
		return t;
	});

	private Set<WindowKey> lastWatched = new HashSet<>();
	private @Nullable Frustum frustum = null;
	private int tickCounter = 0;

	private static class RemoteWindow {
		final WindowKey key;
		final ResourceLocation location;
		DynamicTexture texture = null;
		int width = 0;
		int height = 0;

		// Chunks of the frame currently being received
		int assemblingFrame = -1;
		byte[][] chunks = null;
		int received = 0;

		RemoteAudioPlayer audio = null;
		@Nullable Vec3 audioPosition = null;

		RemoteWindow(WindowKey key) {
			this.key = key;
			this.location = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID,
				"shared/" + key.owner() + "_" + Long.toHexString(key.handle()));
		}
	}

	// Frustum of the last rendered level frame, for the visibility check
	public void setFrustum(Frustum frustum) {
		this.frustum = frustum;
	}

	public static @Nullable WindowKey keyOf(ItemStack itemStack) {
		if(!itemStack.is(WindowItem.WINDOW)) return null;
		WindowHandle handle = itemStack.get(WindowItem.WINDOW_HANDLE);
		if(handle == null) return null;
		return new WindowKey(handle.player(), handle.handle());
	}

	public boolean hasFrame(ItemStack itemStack) {
		WindowKey key = keyOf(itemStack);
		if(key == null) return false;
		RemoteWindow window = windows.get(key);
		return window != null && window.texture != null;
	}

	/* Visibility */

	public void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if(minecraft.level == null || minecraft.player == null) return;
		if(++tickCounter % WATCH_INTERVAL_TICKS != 0) return;

		Set<WindowKey> watched = new HashSet<>();
		Map<WindowKey, Vec3> audioPositions = new HashMap<>();
		Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();

		for(Entity entity : minecraft.level.entitiesForRendering()) {
			if(!(entity instanceof ItemFrame frame)) continue;
			WindowKey key = keyOf(frame.getItem());
			if(key == null) continue;
			if(key.owner().equals(minecraft.player.getUUID())) continue; // Own windows render locally

			Vec3 center = frame.getBoundingBox().getCenter();
			audioPositions.putIfAbsent(key, center);

			if(center.distanceTo(eye) > WATCH_RANGE) continue;
			if(frustum != null && !frustum.isVisible(frame.getBoundingBox().inflate(0.5))) continue;
			if(!inLineOfSight(eye, frame)) continue;
			watched.add(key);
		}

		// Keep audio sources at their frames
		for(RemoteWindow window : windows.values()) {
			window.audioPosition = audioPositions.get(window.key);
			if(window.audio != null && window.audioPosition != null) window.audio.setPosition(window.audioPosition);
		}

		if(!watched.equals(lastWatched)) {
			lastWatched = watched;
			WaylandCraftNetworking.sendToServer(new WatchPayload(new ArrayList<>(watched)));
		}
	}

	// A frame is visible if nothing blocks the ray to a point just in front of it
	private static boolean inLineOfSight(Vec3 eye, ItemFrame frame) {
		Vec3 facing = Vec3.atLowerCornerOf(frame.getDirection().getNormal());
		Vec3 target = frame.getBoundingBox().getCenter().add(facing.scale(0.1));

		Minecraft minecraft = Minecraft.getInstance();
		HitResult hit = minecraft.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player));
		return hit.getType() == HitResult.Type.MISS;
	}

	/* Video */

	public void onStreamVideo(StreamVideoPayload payload) {
		if(payload.count() < 1 || payload.count() > SharingNetworking.MAX_CHUNKS_PER_FRAME) return;
		if(payload.index() < 0 || payload.index() >= payload.count()) return;

		RemoteWindow window = windows.computeIfAbsent(payload.key(), RemoteWindow::new);
		if(payload.frame() != window.assemblingFrame) {
			// A new frame starts; an incomplete older frame is dropped
			window.assemblingFrame = payload.frame();
			window.chunks = new byte[payload.count()][];
			window.received = 0;
		}
		if(window.chunks.length != payload.count() || window.chunks[payload.index()] != null) return;

		window.chunks[payload.index()] = payload.data();
		if(++window.received < window.chunks.length) return;

		byte[][] chunks = window.chunks;
		window.chunks = null;
		window.assemblingFrame = -1;
		decoder.execute(() -> decode(window, chunks));
	}

	private void decode(RemoteWindow window, byte[][] chunks) {
		int length = 0;
		for(byte[] chunk : chunks) length += chunk.length;
		byte[] jpeg = new byte[length];
		int offset = 0;
		for(byte[] chunk : chunks) {
			System.arraycopy(chunk, 0, jpeg, offset, chunk.length);
			offset += chunk.length;
		}

		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(jpeg));
		} catch(IOException e) {
			return;
		}
		if(image == null) return;

		NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, image.getWidth(), image.getHeight(), false);
		for(int y = 0; y < image.getHeight(); y++) {
			for(int x = 0; x < image.getWidth(); x++) {
				int rgb = image.getRGB(x, y);
				// NativeImage pixels are ABGR
				nativeImage.setPixelRGBA(x, y, 0xff000000 | (rgb & 0xff) << 16 | (rgb & 0xff00) | (rgb >> 16 & 0xff));
			}
		}

		Minecraft.getInstance().execute(() -> upload(window, nativeImage));
	}

	private void upload(RemoteWindow window, NativeImage image) {
		if(windows.get(window.key) != window) {
			image.close();
			return;
		}

		if(window.texture != null && window.width == image.getWidth() && window.height == image.getHeight()) {
			window.texture.setPixels(image);
			window.texture.upload();
			return;
		}

		// Size changed (or first frame): register a new texture, which also closes the old one
		window.texture = new DynamicTexture(image);
		window.width = image.getWidth();
		window.height = image.getHeight();
		Minecraft.getInstance().getTextureManager().register(window.location, window.texture);
	}

	// Called from RenderItemInFrameEvent with vanilla's item-in-frame pose (before its 0.5 item scale)
	public boolean renderInFrame(ItemStack itemStack, PoseStack poseStack, MultiBufferSource buffers) {
		WindowKey key = keyOf(itemStack);
		if(key == null) return false;
		RemoteWindow window = windows.get(key);
		if(window == null || window.texture == null) return false;

		// Same placement as WindowInItemFrameRenderer: fit the longer side to the frame
		float scale = 1.0f / Math.max(window.width, window.height);
		float w = window.width * scale;
		float h = window.height * scale;

		poseStack.pushPose();
		poseStack.translate(0.0f, 0.0f, -0.005f);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180));
		poseStack.translate(-w / 2, -h / 2, 0.0f);

		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(window.location));
		addQuad(poseStack.last(), buffer, w, h);
		poseStack.popPose();
		return true;
	}

	private static void addQuad(Pose pose, VertexConsumer buffer, float w, float h) {
		int color = FastColor.ARGB32.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f);
		int overlay = OverlayTexture.NO_OVERLAY;
		int light = LightTexture.FULL_BRIGHT;
		Vector3f normal = pose.transformNormal(0, 0, -1, new Vector3f());

		// Texture row 0 is the window's top row; the frame pose is rotated 180 degrees
		Vector3f p1 = pose.pose().transformPosition(0, 0, 0, new Vector3f());
		Vector3f p2 = pose.pose().transformPosition(0, h, 0, new Vector3f());
		Vector3f p3 = pose.pose().transformPosition(w, h, 0, new Vector3f());
		Vector3f p4 = pose.pose().transformPosition(w, 0, 0, new Vector3f());

		// Same winding as RenderUtils' front face, so back-face culling hides it from behind
		buffer.addVertex(p1.x, p1.y, p1.z, color, 0.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
		buffer.addVertex(p2.x, p2.y, p2.z, color, 0.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
		buffer.addVertex(p3.x, p3.y, p3.z, color, 1.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
		buffer.addVertex(p4.x, p4.y, p4.z, color, 1.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
	}

	/* Audio */

	public void onStreamAudio(StreamAudioPayload payload) {
		RemoteWindow window = windows.computeIfAbsent(payload.key(), RemoteWindow::new);
		if(window.audio == null) {
			window.audio = new RemoteAudioPlayer();
			if(window.audioPosition != null) window.audio.setPosition(window.audioPosition);
		}
		window.audio.queue(payload.pcm());
	}

	/* Lifecycle */

	public void onStreamEnd(StreamEndPayload payload) {
		remove(payload.key());
	}

	public void reset() {
		for(WindowKey key : new ArrayList<>(windows.keySet())) remove(key);
		lastWatched = new HashSet<>();
	}

	private void remove(WindowKey key) {
		RemoteWindow window = windows.remove(key);
		if(window == null) return;
		if(window.audio != null) window.audio.close();
		if(window.texture != null) Minecraft.getInstance().getTextureManager().release(window.location);
	}


}
