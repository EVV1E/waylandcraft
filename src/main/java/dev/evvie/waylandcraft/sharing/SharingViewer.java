package dev.evvie.waylandcraft.sharing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
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
import dev.evvie.waylandcraft.sharing.SharingNetworking.FrameInfo;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamAudioPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamEndPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamPosePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamVideoPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WatchPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowKey;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowPose;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/* Viewer side of window sharing: other players' shared windows, shown in item frames or
 * as free-floating displays where their owner placed them.
 *
 * Video is demand-driven. Every few ticks the viewer checks which frames with other
 * players' windows are in view (frustum) and in line of sight (no blocks in between),
 * and tells the server; only those streams are sent. Audio is sent by the server to
 * everyone in range regardless of line of sight, and plays positionally at the frame.
 */
public class SharingViewer {

	private static final double WATCH_RANGE = 64.0;
	private static final int WATCH_INTERVAL_TICKS = 5;

	// Decoded frames waiting for the audio clock to reach them
	private static final int MAX_PENDING_FRAMES = 30;
	// Frames this far ahead of the audio clock are shown anyway (clock mismatch or stalled audio)
	private static final long MAX_SYNC_DELAY_MILLIS = 1500;
	// Half the spread between a framed window's left and right audio sources, in blocks
	private static final double FRAME_STEREO_HALF_WIDTH = 0.4;

	private final Map<WindowKey, RemoteWindow> windows = new HashMap<>();
	private final ExecutorService decoder = Executors.newSingleThreadExecutor((r) -> {
		Thread t = new Thread(r, "WaylandCraft sharing decoder");
		t.setDaemon(true);
		return t;
	});

	private Set<WindowKey> lastWatched = new HashSet<>();
	private @Nullable Frustum frustum = null;
	private int tickCounter = 0;

	private static record PendingFrame(NativeImage image, long timestamp) {}

	private static record AudioPosition(Vec3 left, Vec3 right) {}

	private static class RemoteWindow {
		final WindowKey key;
		final ResourceLocation location;
		DynamicTexture texture = null;
		int width = 0;
		int height = 0;

		// Chunks of the frame currently being received
		int assemblingFrame = -1;
		FrameInfo assemblingInfo = null;
		byte[][] chunks = null;
		int received = 0;

		// H.264 decoding, only touched on the decoder thread. Deltas need an unbroken
		// chain back to a key frame; until one arrives they are skipped.
		final H264Codec.Decoder h264 = new H264Codec.Decoder();
		volatile boolean needKeyFrame = true;

		// Decoded frames with their capture timestamps, shown in step with the audio
		final ArrayDeque<PendingFrame> pending = new ArrayDeque<>();

		RemoteAudioPlayer audio = null;
		@Nullable AudioPosition audioPosition = null;

		// Set while the owner shows the window as a free-floating display
		@Nullable WindowPose pose = null;

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
		Map<WindowKey, AudioPosition> audioPositions = new HashMap<>();
		Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();

		// Free-floating displays of other players' shared windows
		for(RemoteWindow window : windows.values()) {
			WindowPose pose = window.pose;
			if(pose == null) continue;
			Vec3 halfWidth = pose.right().scale(pose.width() / 2);
			audioPositions.putIfAbsent(window.key, new AudioPosition(pose.pivot().subtract(halfWidth), pose.pivot().add(halfWidth)));

			if(pose.pivot().distanceTo(eye) > WATCH_RANGE) continue;
			if(frustum != null && !frustum.isVisible(bounds(pose))) continue;
			if(!inLineOfSight(eye, pose.pivot().add(pose.normal().scale(0.05)))) continue;
			watched.add(window.key);
		}

		for(Entity entity : minecraft.level.entitiesForRendering()) {
			if(!(entity instanceof ItemFrame frame)) continue;
			WindowKey key = keyOf(frame.getItem());
			if(key == null) continue;
			if(key.owner().equals(minecraft.player.getUUID())) continue; // Own windows render locally

			Vec3 center = frame.getBoundingBox().getCenter();
			Vec3 facing = Vec3.atLowerCornerOf(frame.getDirection().getNormal());
			// Right, as seen by someone facing the frame; frames on floors and ceilings play centered
			Vec3 right = Math.abs(facing.y) > 0.5 ? Vec3.ZERO : facing.reverse().cross(new Vec3(0, 1, 0)).scale(FRAME_STEREO_HALF_WIDTH);
			audioPositions.putIfAbsent(key, new AudioPosition(center.subtract(right), center.add(right)));

			if(center.distanceTo(eye) > WATCH_RANGE) continue;
			if(frustum != null && !frustum.isVisible(frame.getBoundingBox().inflate(0.5))) continue;
			if(!inLineOfSight(eye, center.add(facing.scale(0.1)))) continue;
			watched.add(key);
		}

		// Keep audio sources at their frames
		for(RemoteWindow window : windows.values()) {
			window.audioPosition = audioPositions.get(window.key);
			if(window.audio != null && window.audioPosition != null) window.audio.setPosition(window.audioPosition.left(), window.audioPosition.right());
		}

		if(!watched.equals(lastWatched)) {
			lastWatched = watched;
			WaylandCraftNetworking.sendToServer(new WatchPayload(new ArrayList<>(watched)));
		}
	}

	// Visible if nothing blocks the ray to a point just in front of the window
	private static boolean inLineOfSight(Vec3 eye, Vec3 target) {
		Minecraft minecraft = Minecraft.getInstance();
		HitResult hit = minecraft.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player));
		return hit.getType() == HitResult.Type.MISS;
	}

	private static AABB bounds(WindowPose pose) {
		Vec3 halfX = pose.right().scale(pose.width() / 2);
		Vec3 halfY = pose.down().scale(pose.height() / 2);
		return new AABB(pose.pivot().subtract(halfX).subtract(halfY), pose.pivot().add(halfX).add(halfY)).inflate(0.1);
	}

	public void onStreamPose(StreamPosePayload payload) {
		windows.computeIfAbsent(payload.key(), RemoteWindow::new).pose = payload.pose();
	}

	/* Video */

	public void onStreamVideo(StreamVideoPayload payload) {
		FrameInfo info = payload.info();
		if(!info.valid()) return;

		RemoteWindow window = windows.computeIfAbsent(payload.key(), RemoteWindow::new);
		if(info.frame() != window.assemblingFrame) {
			// A new frame starts; an incomplete older frame is dropped, which breaks the H.264 chain
			if(window.assemblingInfo != null && window.assemblingInfo.kind() != SharingNetworking.FRAME_STILL) window.needKeyFrame = true;
			window.assemblingFrame = info.frame();
			window.assemblingInfo = info;
			window.chunks = new byte[info.count()][];
			window.received = 0;
		}
		if(window.chunks.length != info.count() || window.chunks[info.index()] != null) return;

		window.chunks[info.index()] = payload.data();
		if(++window.received < window.chunks.length) return;

		byte[][] chunks = window.chunks;
		FrameInfo frameInfo = window.assemblingInfo;
		window.chunks = null;
		window.assemblingInfo = null;
		window.assemblingFrame = -1;
		decoder.execute(() -> decode(window, frameInfo, chunks));
	}

	// Decoder thread. Frames are decoded in arrival order, which H.264 deltas rely on.
	private void decode(RemoteWindow window, FrameInfo info, byte[][] chunks) {
		int length = 0;
		for(byte[] chunk : chunks) length += chunk.length;
		byte[] data = new byte[length];
		int offset = 0;
		for(byte[] chunk : chunks) {
			System.arraycopy(chunk, 0, data, offset, chunk.length);
			offset += chunk.length;
		}

		NativeImage image;
		if(info.kind() == SharingNetworking.FRAME_STILL) {
			image = decodePng(data);
		}
		else {
			if(info.kind() == SharingNetworking.FRAME_DELTA && window.needKeyFrame) return;
			image = window.h264.decode(data, info.width(), info.height());
			// Undecodable frames break the chain until the next key frame
			window.needKeyFrame = image == null;
		}
		if(image == null) return;

		Minecraft.getInstance().execute(() -> {
			if(windows.get(window.key) != window) {
				image.close();
				return;
			}
			window.pending.addLast(new PendingFrame(image, info.timestamp()));
			while(window.pending.size() > MAX_PENDING_FRAMES) window.pending.pollFirst().image().close();
		});
	}

	private static @Nullable NativeImage decodePng(byte[] data) {
		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(data));
		} catch(IOException e) {
			return null;
		}
		if(image == null) return null;

		NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, image.getWidth(), image.getHeight(), false);
		for(int y = 0; y < image.getHeight(); y++) {
			for(int x = 0; x < image.getWidth(); x++) {
				int rgb = image.getRGB(x, y);
				// NativeImage pixels are ABGR
				nativeImage.setPixelRGBA(x, y, 0xff000000 | (rgb & 0xff) << 16 | (rgb & 0xff00) | (rgb >> 16 & 0xff));
			}
		}
		return nativeImage;
	}

	/* A/V sync: every render frame, show the newest decoded frame whose capture time the
	 * audio has reached. Without audio (or if the clocks disagree badly), frames show as
	 * soon as they're decoded.
	 */
	public void presentFrames() {
		for(RemoteWindow window : windows.values()) {
			if(window.pending.isEmpty()) continue;

			long clock = window.audio != null ? window.audio.playbackTimestamp() : -1;
			PendingFrame due = null;
			while(!window.pending.isEmpty()) {
				PendingFrame next = window.pending.peekFirst();
				boolean ready = clock < 0 || next.timestamp() <= clock || next.timestamp() - clock > MAX_SYNC_DELAY_MILLIS;
				if(!ready) break;

				window.pending.pollFirst();
				if(due != null) due.image().close();
				due = next;
			}
			if(due != null) upload(window, due.image());
		}
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

	// Draws other players' free-floating shared windows. poseStack is camera-relative.
	public void renderFloating(PoseStack poseStack, MultiBufferSource buffers, Vec3 cameraPos) {
		for(RemoteWindow window : windows.values()) {
			WindowPose pose = window.pose;
			if(pose == null || window.texture == null) continue;

			Vec3 spanX = pose.right().scale(pose.width());
			Vec3 spanY = pose.down().scale(pose.height());
			Vec3 origin = pose.pivot().subtract(spanX.scale(0.5)).subtract(spanY.scale(0.5)).subtract(cameraPos);

			// Window on the front, black silhouette on the back, like local displays under shader packs
			VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(window.location));
			addWorldQuad(poseStack.last(), buffer, origin, spanX, spanY, FastColor.ARGB32.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f), false);
			addWorldQuad(poseStack.last(), buffer, origin, spanX, spanY, FastColor.ARGB32.colorFromFloat(1.0f, 0.0f, 0.0f, 0.0f), true);
		}
	}

	private static void addWorldQuad(Pose pose, VertexConsumer buffer, Vec3 origin, Vec3 spanX, Vec3 spanY, int color, boolean reverse) {
		Vector3f tl = pose.pose().transformPosition(origin.toVector3f());
		Vector3f bl = pose.pose().transformPosition(origin.add(spanY).toVector3f());
		Vector3f br = pose.pose().transformPosition(origin.add(spanY).add(spanX).toVector3f());
		Vector3f tr = pose.pose().transformPosition(origin.add(spanX).toVector3f());
		Vector3f normal = pose.transformNormal(spanY.cross(spanX).normalize().toVector3f(), new Vector3f());
		int overlay = OverlayTexture.NO_OVERLAY;
		int light = LightTexture.FULL_BRIGHT;

		// Same winding as RenderUtils' front and back faces
		if(!reverse) {
			buffer.addVertex(tl.x, tl.y, tl.z, color, 0.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(bl.x, bl.y, bl.z, color, 0.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(br.x, br.y, br.z, color, 1.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(tr.x, tr.y, tr.z, color, 1.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
		}
		else {
			buffer.addVertex(tr.x, tr.y, tr.z, color, 1.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(br.x, br.y, br.z, color, 1.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(bl.x, bl.y, bl.z, color, 0.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(tl.x, tl.y, tl.z, color, 0.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
		}
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
			if(window.audioPosition != null) window.audio.setPosition(window.audioPosition.left(), window.audioPosition.right());
		}
		window.audio.queue(payload.opus(), payload.timestamp());
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
		while(!window.pending.isEmpty()) window.pending.pollFirst().image().close();
	}


}
