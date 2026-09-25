package dev.evvie.waylandcraft.sharing;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.sharing.SharingNetworking.FrameInfo;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowKey;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowPose;
import net.minecraft.server.level.ServerLevel;

/* A server-side fake shared window for testing sharing without a second player or a
 * real window (/waylandcraft testpattern). It plugs into SharingServer like a real
 * owner: it only generates video while someone can see it and audio while someone is
 * in range, and it answers key frame requests, so the relay, visibility checks,
 * budgets, decoding, A/V sync and stereo placement are all exercised.
 *
 * - Video: SMPTE-style color bars with a box sweeping across, H.264 (JCodec, pure Java)
 *   at 10 fps.
 * - Audio: a short 880 Hz beep every second, alternating the left and right channel,
 *   stereo Opus.
 * - Sync check: the picture gets a white border while a beep plays. Both use the same
 *   clock, so the flash and the beep should coincide on the viewer.
 * - Stereo check: facing the window, beeps alternate between its left and right edge.
 */
public class TestPatternSource {

	// Owner of all test pattern windows; never a real player
	public static final UUID OWNER = UUID.nameUUIDFromBytes("waylandcraft:test-pattern".getBytes());

	static final int WIDTH = 320;
	static final int HEIGHT = 240;
	private static final long FRAME_INTERVAL_MILLIS = 100;
	private static final int KEY_FRAME_INTERVAL = 30;

	private static final long BEEP_MILLIS = 150;
	private static final double BEEP_HZ = 880.0;
	private static final int AUDIO_BITRATE = 96000;
	// Cap on audio generated per tick after a stall, in 20 ms packets
	private static final int MAX_CATCH_UP_PACKETS = 10;

	private static final int[] BARS = {0xc0c0c0, 0xc0c000, 0x00c0c0, 0x00c000, 0xc000c0, 0xc00000, 0x0000c0};

	final WindowKey key;
	final ServerLevel level;
	final WindowPose pose;

	private final H264Codec.Encoder video = new H264Codec.Encoder(WIDTH, HEIGHT);
	private final ByteBuffer rgba = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
	private final OpusEncoder audio;
	private final short[] samples = new short[SharingNetworking.AUDIO_FRAME_SAMPLES * SharingNetworking.AUDIO_CHANNELS];
	private final byte[] packet = new byte[SharingNetworking.MAX_AUDIO_BYTES];

	private long lastFrameTime = 0;
	private int frameId = 0;
	private int framesSinceKey = 0;
	private boolean forceKeyFrame = true;
	// Capture time of the next audio packet; -1 until audio is first needed
	private long nextAudioTime = -1;

	TestPatternSource(long handle, ServerLevel level, WindowPose pose) {
		this.key = new WindowKey(OWNER, handle);
		this.level = level;
		this.pose = pose;
		try {
			audio = new OpusEncoder(SharingNetworking.AUDIO_SAMPLE_RATE, SharingNetworking.AUDIO_CHANNELS, OpusApplication.OPUS_APPLICATION_AUDIO);
			audio.setBitrate(AUDIO_BITRATE);
		} catch(OpusException e) {
			throw new IllegalStateException("Failed to create Opus encoder", e);
		}
	}

	void requestKeyFrame() {
		forceKeyFrame = true;
	}

	// Server thread, every tick
	void tick(SharingServer server) {
		long now = System.currentTimeMillis();

		if(server.hasVideoDemand(key) && now - lastFrameTime >= FRAME_INTERVAL_MILLIS) {
			lastFrameTime = now;
			sendFrame(server, now);
		}

		if(server.hasAudioDemand(key)) {
			if(nextAudioTime < 0 || now - nextAudioTime > MAX_CATCH_UP_PACKETS * SharingNetworking.AUDIO_FRAME_MILLIS) nextAudioTime = now;
			while(nextAudioTime <= now) {
				sendAudio(server, nextAudioTime);
				nextAudioTime += SharingNetworking.AUDIO_FRAME_MILLIS;
			}
		}
		else {
			nextAudioTime = -1;
		}
	}

	private static boolean beeping(long time) {
		return Math.floorMod(time, 1000) < BEEP_MILLIS;
	}

	private void sendFrame(SharingServer server, long time) {
		drawFrame(time);

		boolean key = forceKeyFrame || framesSinceKey >= KEY_FRAME_INTERVAL;
		forceKeyFrame = false;
		framesSinceKey = key ? 0 : framesSinceKey + 1;
		byte[] data = video.encode(rgba, key);

		int id = ++frameId;
		int chunkSize = SharingNetworking.MAX_CHUNK_BYTES;
		int count = Math.max(1, (data.length + chunkSize - 1) / chunkSize);
		byte kind = key ? SharingNetworking.FRAME_KEY : SharingNetworking.FRAME_DELTA;
		for(int i = 0; i < count; i++) {
			byte[] chunk = Arrays.copyOfRange(data, i * chunkSize, Math.min(data.length, (i + 1) * chunkSize));
			server.relayVideo(this.key, new FrameInfo(id, kind, WIDTH, HEIGHT, time, i, count), chunk);
		}
	}

	private void drawFrame(long time) {
		int barWidth = WIDTH / BARS.length + 1;
		int boxX = (int) (time / 10 % (WIDTH - 40));
		boolean flash = beeping(time);

		for(int y = 0; y < HEIGHT; y++) {
			for(int x = 0; x < WIDTH; x++) {
				int color = y < HEIGHT * 3 / 4 ? BARS[x / barWidth] : (x * 255 / WIDTH) * 0x010101; // gray ramp at the bottom
				if(x >= boxX && x < boxX + 40 && y >= HEIGHT / 3 && y < HEIGHT / 3 + 40) color = 0x000000;
				if(flash && (x < 12 || y < 12 || x >= WIDTH - 12 || y >= HEIGHT - 12)) color = 0xffffff;

				int i = (y * WIDTH + x) * 4;
				rgba.put(i, (byte) (color >> 16));
				rgba.put(i + 1, (byte) (color >> 8));
				rgba.put(i + 2, (byte) color);
				rgba.put(i + 3, (byte) 0xff);
			}
		}
	}

	private void sendAudio(SharingServer server, long time) {
		// Beeps alternate channels every second: left on even seconds, right on odd ones
		int channel = (int) Math.floorMod(time / 1000, 2);
		for(int i = 0; i < SharingNetworking.AUDIO_FRAME_SAMPLES; i++) {
			long sampleTime = time * SharingNetworking.AUDIO_SAMPLE_RATE / 1000 + i;
			double t = (double) sampleTime / SharingNetworking.AUDIO_SAMPLE_RATE;
			boolean on = beeping(sampleTime * 1000 / SharingNetworking.AUDIO_SAMPLE_RATE);
			short value = on ? (short) (8000 * Math.sin(2 * Math.PI * BEEP_HZ * t)) : 0;
			samples[i * 2] = channel == 0 ? value : 0;
			samples[i * 2 + 1] = channel == 1 ? value : 0;
		}

		try {
			int length = audio.encode(samples, 0, SharingNetworking.AUDIO_FRAME_SAMPLES, packet, 0, packet.length);
			server.relayAudio(key, time, Arrays.copyOf(packet, length));
		} catch(OpusException e) {
			WaylandCraftCommon.LOGGER.error("Test pattern audio encoding failed", e);
		}
	}

}
