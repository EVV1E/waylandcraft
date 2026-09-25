package dev.evvie.waylandcraft.sharing;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;

/* Captures the audio a window's application plays, through PipeWire.
 *
 * Wayland doesn't carry audio, so the window is mapped to its client's process id, the
 * process (or one of its children) is matched to a PipeWire playback stream with pw-dump,
 * and pw-record captures only that stream. The application keeps playing locally as usual.
 * Audio is encoded to Opus (pure Java, Concentus) so viewers on any platform can decode it.
 */
public class AudioCapture {

	// One Opus frame of 16-bit mono PCM
	private static final int FRAME_BYTES = SharingNetworking.AUDIO_FRAME_SAMPLES * 2;
	private static final int BITRATE = 64000;

	private static final long LOOKUP_INTERVAL_MILLIS = 2000;

	// Re-evaluated on every stream lookup: resolving an X11 window's pid can succeed later
	private final IntSupplier pid;
	// Receives one Opus packet per 20 ms
	private final Consumer<byte[]> sink;

	private volatile @Nullable Process recorder = null;
	private volatile boolean stopped = false;
	private boolean lookupRunning = false;
	private long lastLookup = 0;

	public AudioCapture(IntSupplier pid, Consumer<byte[]> sink) {
		this.pid = pid;
		this.sink = sink;
	}

	// Starts capturing once the application has a playback stream. Call periodically;
	// the lookup (pw-dump, xprop) runs on a background thread.
	public synchronized void update() {
		if(stopped || lookupRunning) return;
		if(recorder != null && recorder.isAlive()) return;

		long now = System.currentTimeMillis();
		if(now - lastLookup < LOOKUP_INTERVAL_MILLIS) return;
		lastLookup = now;
		lookupRunning = true;

		Thread lookup = new Thread(() -> {
			try {
				int pid = this.pid.getAsInt();
				String serial = pid > 0 ? findStreamSerial(pid) : null;
				if(serial != null) start(serial);
			} finally {
				synchronized(this) {
					lookupRunning = false;
				}
			}
		}, "WaylandCraft audio lookup");
		lookup.setDaemon(true);
		lookup.start();
	}

	public synchronized void stop() {
		stopped = true;
		if(recorder != null) recorder.destroy();
		recorder = null;
	}

	private synchronized void start(String serial) {
		if(stopped) return;

		ProcessBuilder builder = new ProcessBuilder(
			"pw-record", "--raw",
			"--target", serial,
			// Never fall back to another node (e.g. the microphone) if the stream goes away
			"-P", "{ node.dont-fallback = true, node.dont-reconnect = true, node.description = \"WaylandCraft window sharing\" }",
			"--rate", String.valueOf(SharingNetworking.AUDIO_SAMPLE_RATE),
			"--channels", "1",
			"--format", "s16",
			"--latency", "50ms",
			"-");
		builder.redirectError(ProcessBuilder.Redirect.DISCARD);

		try {
			recorder = builder.start();
		} catch(IOException e) {
			WaylandCraftCommon.LOGGER.error("Failed to start pw-record for window audio sharing", e);
			recorder = null;
			return;
		}

		Process process = recorder;
		Thread reader = new Thread(() -> readLoop(process), "WaylandCraft audio capture");
		reader.setDaemon(true);
		reader.start();
	}

	private void readLoop(Process process) {
		OpusEncoder encoder;
		try {
			encoder = new OpusEncoder(SharingNetworking.AUDIO_SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
			encoder.setBitrate(BITRATE);
		} catch(OpusException e) {
			WaylandCraftCommon.LOGGER.error("Failed to create Opus encoder for window audio sharing", e);
			process.destroy();
			return;
		}

		try(InputStream in = process.getInputStream()) {
			byte[] frame = new byte[FRAME_BYTES];
			short[] samples = new short[SharingNetworking.AUDIO_FRAME_SAMPLES];
			byte[] packet = new byte[SharingNetworking.MAX_AUDIO_BYTES];
			while(in.readNBytes(frame, 0, frame.length) == frame.length) {
				ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
				int length = encoder.encode(samples, 0, samples.length, packet, 0, packet.length);
				sink.accept(Arrays.copyOf(packet, length));
			}
		} catch(IOException | OpusException e) {
			// Recorder was stopped
		}
	}

	// Object serial of a PipeWire playback stream owned by the window's process or a child of it
	private static @Nullable String findStreamSerial(int pid) {
		try {
			Process dump = new ProcessBuilder("pw-dump").redirectError(ProcessBuilder.Redirect.DISCARD).start();
			JsonElement root;
			try(InputStreamReader reader = new InputStreamReader(dump.getInputStream())) {
				root = JsonParser.parseReader(reader);
			}

			// Native PipeWire clients carry the pid on the client object, which stream nodes
			// reference by client.id; PulseAudio clients carry it on the node itself
			Map<String, String> clientPids = new HashMap<>();
			for(JsonElement element : root.getAsJsonArray()) {
				JsonObject object = element.getAsJsonObject();
				JsonObject props = props(object);
				if(props == null || !"PipeWire:Interface:Client".equals(prop(object, "type"))) continue;
				String clientPid = prop(props, "application.process.id");
				if(clientPid == null) clientPid = prop(props, "pipewire.sec.pid");
				if(clientPid != null) clientPids.put(prop(object, "id"), clientPid);
			}

			for(JsonElement element : root.getAsJsonArray()) {
				JsonObject props = props(element.getAsJsonObject());
				if(props == null) continue;
				if(!"Stream/Output/Audio".equals(prop(props, "media.class"))) continue;

				String serial = prop(props, "object.serial");
				String streamPid = prop(props, "application.process.id");
				if(streamPid == null) streamPid = clientPids.get(prop(props, "client.id"));
				if(serial == null || streamPid == null) continue;

				if(isSameOrDescendant(Integer.parseInt(streamPid), pid)) return serial;
			}
		} catch(IOException | RuntimeException e) {
			WaylandCraftCommon.LOGGER.warn("Could not look up PipeWire streams for window audio sharing: " + e);
		}
		return null;
	}

	private static @Nullable JsonObject props(JsonObject object) {
		if(!object.has("info") || !object.get("info").isJsonObject()) return null;
		JsonObject info = object.getAsJsonObject("info");
		if(!info.has("props") || !info.get("props").isJsonObject()) return null;
		return info.getAsJsonObject("props");
	}

	private static @Nullable String prop(JsonObject props, String key) {
		if(key == null || !props.has(key) || props.get(key).isJsonNull()) return null;
		return props.get(key).getAsString();
	}

	/* X11 windows reach the compositor through xwayland-satellite, so their Wayland client
	 * is satellite itself. Resolve the real application's pid from the X server instead:
	 * the managed window whose title matches, via _NET_CLIENT_LIST and _NET_WM_PID.
	 * Returns `pid` unchanged for native Wayland clients, and -1 if the X11 lookup fails.
	 */
	public static int resolveX11Pid(int pid, @Nullable String x11Display, @Nullable String title) {
		if(pid <= 0 || !isXwaylandSatellite(pid)) return pid;
		if(x11Display == null || title == null) return -1;

		String clients = run("xprop", "-display", x11Display, "-root", "_NET_CLIENT_LIST");
		if(clients == null || !clients.contains("#")) return -1;

		for(String id : clients.substring(clients.indexOf('#') + 1).split(",")) {
			String props = run("xprop", "-display", x11Display, "-id", id.trim(), "_NET_WM_PID", "_NET_WM_NAME");
			if(props == null || !props.contains("\"" + title + "\"")) continue;

			for(String line : props.split("\n")) {
				if(line.startsWith("_NET_WM_PID") && line.contains("=")) {
					try {
						return Integer.parseInt(line.substring(line.indexOf('=') + 1).trim());
					} catch(NumberFormatException e) {
						return -1;
					}
				}
			}
		}
		return -1;
	}

	private static boolean isXwaylandSatellite(int pid) {
		try {
			// comm is truncated to 15 characters
			return Files.readString(Path.of("/proc", String.valueOf(pid), "comm")).trim().startsWith("xwayland-satel");
		} catch(IOException e) {
			return false;
		}
	}

	private static @Nullable String run(String... command) {
		try {
			Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			String output = new String(process.getInputStream().readAllBytes());
			return process.waitFor() == 0 ? output : null;
		} catch(IOException e) {
			return null;
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	// Browsers and similar apps play audio from a child process
	private static boolean isSameOrDescendant(int candidate, int ancestor) {
		int current = candidate;
		for(int depth = 0; depth < 32 && current > 1; depth++) {
			if(current == ancestor) return true;
			current = parentPid(current);
		}
		return false;
	}

	private static int parentPid(int pid) {
		try {
			// Format: pid (comm) state ppid ... ; comm may contain spaces, so parse after the last ')'
			String stat = Files.readString(Path.of("/proc", String.valueOf(pid), "stat"));
			String[] fields = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
			return Integer.parseInt(fields[1]);
		} catch(IOException | RuntimeException e) {
			return -1;
		}
	}

}
