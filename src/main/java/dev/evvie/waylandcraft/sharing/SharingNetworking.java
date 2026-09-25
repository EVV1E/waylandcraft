package dev.evvie.waylandcraft.sharing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/* Payloads for read-only window sharing (see TODO-SHARING.md).
 *
 * Owner -> server: share state, encoded video chunks, Opus audio packets, floating window pose.
 * Viewer -> server: the set of shared windows it can currently see.
 * Server -> owner: whether anyone is watching / listening (so idle windows cost nothing),
 *                  and key frame requests when a viewer joins.
 * Server -> viewers: relayed video, audio and floating window poses, and stream end.
 */
public class SharingNetworking {

	// Serverbound custom payloads are limited to 32767 bytes
	public static final int MAX_CHUNK_BYTES = 30000;
	// Lossless stills of large windows can take many chunks
	public static final int MAX_CHUNKS_PER_FRAME = 64;
	public static final int MAX_AUDIO_BYTES = 4000;

	// Opus, stereo, 20 ms frames
	public static final int AUDIO_SAMPLE_RATE = 48000;
	public static final int AUDIO_CHANNELS = 2;
	public static final int AUDIO_FRAME_SAMPLES = AUDIO_SAMPLE_RATE / 50;
	public static final long AUDIO_FRAME_MILLIS = 20;

	// Video frame kinds. Delta frames depend on every frame since the last key frame;
	// stills are independent lossless PNGs sent once a window stops changing.
	public static final byte FRAME_KEY = 0;
	public static final byte FRAME_DELTA = 1;
	public static final byte FRAME_STILL = 2;

	// Header shared by every chunk of a video frame. timestamp is the owner's capture
	// time in milliseconds; audio packets use the same clock, for A/V sync.
	public static record FrameInfo(int frame, byte kind, int width, int height, long timestamp, int index, int count) {

		static void write(FriendlyByteBuf buf, FrameInfo info) {
			buf.writeVarInt(info.frame);
			buf.writeByte(info.kind);
			buf.writeVarInt(info.width);
			buf.writeVarInt(info.height);
			buf.writeLong(info.timestamp);
			buf.writeVarInt(info.index);
			buf.writeVarInt(info.count);
		}

		static FrameInfo read(FriendlyByteBuf buf) {
			return new FrameInfo(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readVarInt());
		}

		public boolean valid() {
			return kind >= FRAME_KEY && kind <= FRAME_STILL
				&& width > 0 && height > 0 && width <= 4096 && height <= 4096
				&& count >= 1 && count <= MAX_CHUNKS_PER_FRAME && index >= 0 && index < count;
		}

	}

	// A shared window, identified by the owning player and the owner's window handle
	public static record WindowKey(UUID owner, long handle) {

		static void write(FriendlyByteBuf buf, WindowKey key) {
			buf.writeUUID(key.owner);
			buf.writeLong(key.handle);
		}

		static WindowKey read(FriendlyByteBuf buf) {
			return new WindowKey(buf.readUUID(), buf.readLong());
		}

	}

	// World placement of a free-floating (not framed) shared window.
	// width and height are in blocks; normal and down are unit vectors.
	public static record WindowPose(Vec3 pivot, Vec3 normal, Vec3 down, float width, float height) {

		// Maximum accepted window size in blocks
		static final float MAX_SIZE = 32.0f;

		public Vec3 right() {
			return normal.cross(down);
		}

		static void write(FriendlyByteBuf buf, @Nullable WindowPose pose) {
			buf.writeBoolean(pose != null);
			if(pose == null) return;
			buf.writeVec3(pose.pivot);
			buf.writeVec3(pose.normal);
			buf.writeVec3(pose.down);
			buf.writeFloat(pose.width);
			buf.writeFloat(pose.height);
		}

		static @Nullable WindowPose read(FriendlyByteBuf buf) {
			if(!buf.readBoolean()) return null;
			WindowPose pose = new WindowPose(buf.readVec3(), buf.readVec3().normalize(), buf.readVec3().normalize(), buf.readFloat(), buf.readFloat());
			boolean valid = pose.width > 0 && pose.height > 0 && pose.width <= MAX_SIZE && pose.height <= MAX_SIZE
					&& Double.isFinite(pose.pivot.x) && Double.isFinite(pose.pivot.y) && Double.isFinite(pose.pivot.z);
			return valid ? pose : null;
		}

	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
		return new CustomPacketPayload.Type<T>(ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, name));
	}

	/* Owner -> server */

	public static record ShareStatePayload(long handle, boolean shared) implements CustomPacketPayload {
		public static final Type<ShareStatePayload> TYPE = payloadType("share_state");
		public static final StreamCodec<FriendlyByteBuf, ShareStatePayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); buf.writeBoolean(p.shared); },
			(buf) -> new ShareStatePayload(buf.readLong(), buf.readBoolean()));
		@Override public Type<ShareStatePayload> type() { return TYPE; }
	}

	// One chunk of an encoded frame (H.264 while moving, PNG when still). Frames are
	// split because of the serverbound size limit.
	public static record VideoChunkPayload(long handle, FrameInfo info, byte[] data) implements CustomPacketPayload {
		public static final Type<VideoChunkPayload> TYPE = payloadType("video_chunk");
		public static final StreamCodec<FriendlyByteBuf, VideoChunkPayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); FrameInfo.write(buf, p.info); buf.writeByteArray(p.data); },
			(buf) -> new VideoChunkPayload(buf.readLong(), FrameInfo.read(buf), buf.readByteArray(MAX_CHUNK_BYTES)));
		@Override public Type<VideoChunkPayload> type() { return TYPE; }
	}

	// One Opus packet (20 ms of stereo audio), timestamped with the owner's capture clock
	public static record AudioChunkPayload(long handle, long timestamp, byte[] opus) implements CustomPacketPayload {
		public static final Type<AudioChunkPayload> TYPE = payloadType("audio_chunk");
		public static final StreamCodec<FriendlyByteBuf, AudioChunkPayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); buf.writeLong(p.timestamp); buf.writeByteArray(p.opus); },
			(buf) -> new AudioChunkPayload(buf.readLong(), buf.readLong(), buf.readByteArray(MAX_AUDIO_BYTES)));
		@Override public Type<AudioChunkPayload> type() { return TYPE; }
	}

	// Placement of a shared window shown as a free-floating display, or null when it isn't
	public static record DisplayPosePayload(long handle, @Nullable WindowPose pose) implements CustomPacketPayload {
		public static final Type<DisplayPosePayload> TYPE = payloadType("display_pose");
		public static final StreamCodec<FriendlyByteBuf, DisplayPosePayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); WindowPose.write(buf, p.pose); },
			(buf) -> new DisplayPosePayload(buf.readLong(), WindowPose.read(buf)));
		@Override public Type<DisplayPosePayload> type() { return TYPE; }
	}

	/* Viewer -> server */

	// Replaces the viewer's set of windows it wants video for (visible, in line of sight)
	public static record WatchPayload(List<WindowKey> windows) implements CustomPacketPayload {
		public static final Type<WatchPayload> TYPE = payloadType("watch");
		public static final StreamCodec<FriendlyByteBuf, WatchPayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeVarInt(p.windows.size()); p.windows.forEach((k) -> WindowKey.write(buf, k)); },
			(buf) -> {
				int n = Math.min(buf.readVarInt(), 64);
				List<WindowKey> keys = new ArrayList<>(n);
				for(int i = 0; i < n; i++) keys.add(WindowKey.read(buf));
				return new WatchPayload(keys);
			});
		@Override public Type<WatchPayload> type() { return TYPE; }
	}

	/* Server -> owner */

	// A viewer joined and needs a frame it can decode on its own
	public static record KeyFrameRequestPayload(long handle) implements CustomPacketPayload {
		public static final Type<KeyFrameRequestPayload> TYPE = payloadType("key_frame_request");
		public static final StreamCodec<FriendlyByteBuf, KeyFrameRequestPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeLong(p.handle),
			(buf) -> new KeyFrameRequestPayload(buf.readLong()));
		@Override public Type<KeyFrameRequestPayload> type() { return TYPE; }
	}

	public static record DemandPayload(long handle, boolean video, boolean audio) implements CustomPacketPayload {
		public static final Type<DemandPayload> TYPE = payloadType("demand");
		public static final StreamCodec<FriendlyByteBuf, DemandPayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); buf.writeBoolean(p.video); buf.writeBoolean(p.audio); },
			(buf) -> new DemandPayload(buf.readLong(), buf.readBoolean(), buf.readBoolean()));
		@Override public Type<DemandPayload> type() { return TYPE; }
	}

	/* Server -> viewers */

	public static record StreamVideoPayload(WindowKey key, FrameInfo info, byte[] data) implements CustomPacketPayload {
		public static final Type<StreamVideoPayload> TYPE = payloadType("stream_video");
		public static final StreamCodec<FriendlyByteBuf, StreamVideoPayload> CODEC = StreamCodec.of(
			(buf, p) -> { WindowKey.write(buf, p.key); FrameInfo.write(buf, p.info); buf.writeByteArray(p.data); },
			(buf) -> new StreamVideoPayload(WindowKey.read(buf), FrameInfo.read(buf), buf.readByteArray(MAX_CHUNK_BYTES)));
		@Override public Type<StreamVideoPayload> type() { return TYPE; }
	}

	public static record StreamAudioPayload(WindowKey key, long timestamp, byte[] opus) implements CustomPacketPayload {
		public static final Type<StreamAudioPayload> TYPE = payloadType("stream_audio");
		public static final StreamCodec<FriendlyByteBuf, StreamAudioPayload> CODEC = StreamCodec.of(
			(buf, p) -> { WindowKey.write(buf, p.key); buf.writeLong(p.timestamp); buf.writeByteArray(p.opus); },
			(buf) -> new StreamAudioPayload(WindowKey.read(buf), buf.readLong(), buf.readByteArray(MAX_AUDIO_BYTES)));
		@Override public Type<StreamAudioPayload> type() { return TYPE; }
	}

	public static record StreamPosePayload(WindowKey key, @Nullable WindowPose pose) implements CustomPacketPayload {
		public static final Type<StreamPosePayload> TYPE = payloadType("stream_pose");
		public static final StreamCodec<FriendlyByteBuf, StreamPosePayload> CODEC = StreamCodec.of(
			(buf, p) -> { WindowKey.write(buf, p.key); WindowPose.write(buf, p.pose); },
			(buf) -> new StreamPosePayload(WindowKey.read(buf), WindowPose.read(buf)));
		@Override public Type<StreamPosePayload> type() { return TYPE; }
	}

	public static record StreamEndPayload(WindowKey key) implements CustomPacketPayload {
		public static final Type<StreamEndPayload> TYPE = payloadType("stream_end");
		public static final StreamCodec<FriendlyByteBuf, StreamEndPayload> CODEC = StreamCodec.of(
			(buf, p) -> WindowKey.write(buf, p.key),
			(buf) -> new StreamEndPayload(WindowKey.read(buf)));
		@Override public Type<StreamEndPayload> type() { return TYPE; }
	}

	public static void register(PayloadRegistrar registrar, SharingServer server) {
		registrar.playToServer(ShareStatePayload.TYPE, ShareStatePayload.CODEC, (p, ctx) -> server.onShareState((ServerPlayer) ctx.player(), p));
		registrar.playToServer(VideoChunkPayload.TYPE, VideoChunkPayload.CODEC, (p, ctx) -> server.onVideoChunk((ServerPlayer) ctx.player(), p));
		registrar.playToServer(AudioChunkPayload.TYPE, AudioChunkPayload.CODEC, (p, ctx) -> server.onAudioChunk((ServerPlayer) ctx.player(), p));
		registrar.playToServer(DisplayPosePayload.TYPE, DisplayPosePayload.CODEC, (p, ctx) -> server.onDisplayPose((ServerPlayer) ctx.player(), p));
		registrar.playToServer(WatchPayload.TYPE, WatchPayload.CODEC, (p, ctx) -> server.onWatch((ServerPlayer) ctx.player(), p));

		// Client handlers only run on the client, so the client classes they reference never load on a server
		registrar.playToClient(DemandPayload.TYPE, DemandPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingOwner.onDemand(p));
		registrar.playToClient(KeyFrameRequestPayload.TYPE, KeyFrameRequestPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingOwner.onKeyFrameRequest(p));
		registrar.playToClient(StreamVideoPayload.TYPE, StreamVideoPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingViewer.onStreamVideo(p));
		registrar.playToClient(StreamAudioPayload.TYPE, StreamAudioPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingViewer.onStreamAudio(p));
		registrar.playToClient(StreamPosePayload.TYPE, StreamPosePayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingViewer.onStreamPose(p));
		registrar.playToClient(StreamEndPayload.TYPE, StreamEndPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingViewer.onStreamEnd(p));
	}

}
