package dev.evvie.waylandcraft.sharing;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.evvie.waylandcraft.WaylandCraftCommon;

/* Captures the audio a window's application plays, through PipeWire.
 *
 * Wayland doesn't carry audio, so the window is mapped to its client's process id, the
 * process (or one of its children) is matched to a PipeWire playback stream with pw-dump,
 * and pw-record captures only that stream. The application keeps playing locally as usual.
 */
public class AudioCapture {

	// 50 ms of 16-bit mono audio at 24 kHz
	public static final int CHUNK_BYTES = SharingNetworking.AUDIO_SAMPLE_RATE / 20 * 2;

	private static final long LOOKUP_INTERVAL_MILLIS = 2000;

	private final int pid;
	private final Consumer<byte[]> sink;

	private @Nullable Process recorder = null;
	private long lastLookup = 0;

	public AudioCapture(int pid, Consumer<byte[]> sink) {
		this.pid = pid;
		this.sink = sink;
	}

	// Starts capturing once the application has a playback stream. Call periodically.
	public void update() {
		if(pid <= 0) return;
		if(recorder != null && recorder.isAlive()) return;

		long now = System.currentTimeMillis();
		if(now - lastLookup < LOOKUP_INTERVAL_MILLIS) return;
		lastLookup = now;

		String serial = findStreamSerial();
		if(serial != null) start(serial);
	}

	public void stop() {
		if(recorder != null) recorder.destroy();
		recorder = null;
	}

	private void start(String serial) {
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
		Thread reader = new Thread(() -> readLoop(process), "WaylandCraft audio capture " + pid);
		reader.setDaemon(true);
		reader.start();
	}

	private void readLoop(Process process) {
		try(InputStream in = process.getInputStream()) {
			byte[] chunk = new byte[CHUNK_BYTES];
			while(true) {
				int n = in.readNBytes(chunk, 0, chunk.length);
				if(n <= 0) break;
				sink.accept(Arrays.copyOf(chunk, n));
				if(n < chunk.length) break;
			}
		} catch(IOException e) {
			// Recorder was stopped
		}
	}

	// Object serial of a PipeWire playback stream owned by the window's process or a child of it
	private @Nullable String findStreamSerial() {
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
