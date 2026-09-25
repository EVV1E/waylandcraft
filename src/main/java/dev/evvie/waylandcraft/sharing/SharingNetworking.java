package dev.evvie.waylandcraft.sharing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/* Payloads for read-only window sharing (see TODO-SHARING.md).
 *
 * Owner -> server: share state, encoded video chunks, PCM audio chunks.
 * Viewer -> server: the set of shared windows it can currently see.
 * Server -> owner: whether anyone is watching / listening (so idle windows cost nothing).
 * Server -> viewers: relayed video and audio, and stream end.
 */
public class SharingNetworking {

	// Serverbound custom payloads are limited to 32767 bytes
	public static final int MAX_CHUNK_BYTES = 30000;
	public static final int MAX_CHUNKS_PER_FRAME = 16;
	public static final int MAX_AUDIO_BYTES = 8192;

	public static final int AUDIO_SAMPLE_RATE = 24000;

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

	// One chunk of a JPEG frame. Frames are split because of the serverbound size limit.
	public static record VideoChunkPayload(long handle, int frame, int index, int count, byte[] data) implements CustomPacketPayload {
		public static final Type<VideoChunkPayload> TYPE = payloadType("video_chunk");
		public static final StreamCodec<FriendlyByteBuf, VideoChunkPayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); buf.writeVarInt(p.frame); buf.writeVarInt(p.index); buf.writeVarInt(p.count); buf.writeByteArray(p.data); },
			(buf) -> new VideoChunkPayload(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_CHUNK_BYTES)));
		@Override public Type<VideoChunkPayload> type() { return TYPE; }
	}

	// Signed 16-bit little-endian mono PCM at AUDIO_SAMPLE_RATE
	public static record AudioChunkPayload(long handle, byte[] pcm) implements CustomPacketPayload {
		public static final Type<AudioChunkPayload> TYPE = payloadType("audio_chunk");
		public static final StreamCodec<FriendlyByteBuf, AudioChunkPayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); buf.writeByteArray(p.pcm); },
			(buf) -> new AudioChunkPayload(buf.readLong(), buf.readByteArray(MAX_AUDIO_BYTES)));
		@Override public Type<AudioChunkPayload> type() { return TYPE; }
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

	public static record DemandPayload(long handle, boolean video, boolean audio) implements CustomPacketPayload {
		public static final Type<DemandPayload> TYPE = payloadType("demand");
		public static final StreamCodec<FriendlyByteBuf, DemandPayload> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeLong(p.handle); buf.writeBoolean(p.video); buf.writeBoolean(p.audio); },
			(buf) -> new DemandPayload(buf.readLong(), buf.readBoolean(), buf.readBoolean()));
		@Override public Type<DemandPayload> type() { return TYPE; }
	}

	/* Server -> viewers */

	public static record StreamVideoPayload(WindowKey key, int frame, int index, int count, byte[] data) implements CustomPacketPayload {
		public static final Type<StreamVideoPayload> TYPE = payloadType("stream_video");
		public static final StreamCodec<FriendlyByteBuf, StreamVideoPayload> CODEC = StreamCodec.of(
			(buf, p) -> { WindowKey.write(buf, p.key); buf.writeVarInt(p.frame); buf.writeVarInt(p.index); buf.writeVarInt(p.count); buf.writeByteArray(p.data); },
			(buf) -> new StreamVideoPayload(WindowKey.read(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_CHUNK_BYTES)));
		@Override public Type<StreamVideoPayload> type() { return TYPE; }
	}

	public static record StreamAudioPayload(WindowKey key, byte[] pcm) implements CustomPacketPayload {
		public static final Type<StreamAudioPayload> TYPE = payloadType("stream_audio");
		public static final StreamCodec<FriendlyByteBuf, StreamAudioPayload> CODEC = StreamCodec.of(
			(buf, p) -> { WindowKey.write(buf, p.key); buf.writeByteArray(p.pcm); },
			(buf) -> new StreamAudioPayload(WindowKey.read(buf), buf.readByteArray(MAX_AUDIO_BYTES)));
		@Override public Type<StreamAudioPayload> type() { return TYPE; }
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
		registrar.playToServer(WatchPayload.TYPE, WatchPayload.CODEC, (p, ctx) -> server.onWatch((ServerPlayer) ctx.player(), p));

		// Client handlers only run on the client, so the client classes they reference never load on a server
		registrar.playToClient(DemandPayload.TYPE, DemandPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingOwner.onDemand(p));
		registrar.playToClient(StreamVideoPayload.TYPE, StreamVideoPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingViewer.onStreamVideo(p));
		registrar.playToClient(StreamAudioPayload.TYPE, StreamAudioPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingViewer.onStreamAudio(p));
		registrar.playToClient(StreamEndPayload.TYPE, StreamEndPayload.CODEC, (p, ctx) -> WaylandCraft.instance.sharingViewer.onStreamEnd(p));
	}

}
