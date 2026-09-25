package dev.evvie.waylandcraft.sharing;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/* Plays a shared window's stereo Opus audio in the world.
 *
 * OpenAL only spatializes mono sources, so the two channels play from two mono sources
 * placed at the window's left and right edges: in front of the window you hear stereo
 * across it, from further away it blends into one positional sound. Both sources get
 * the same buffers queued and start together, so they stay in step.
 *
 * Minecraft makes its OpenAL context current for the whole process, so this runs on the
 * client thread next to the game's own sound engine.
 *
 * The player also keeps a playback clock (the owner's capture time of the sample being
 * heard), which video frames are synchronized to.
 */
public class RemoteAudioPlayer {

	// Drop audio instead of building up latency when packets arrive faster than they play (20 ms each)
	private static final int MAX_QUEUED_BUFFERS = 15;
	// Buffered audio before (re)starting playback, to absorb network jitter
	private static final int START_BUFFERS = 5;

	private final int left;
	private final int right;
	private final OpusDecoder decoder;
	private final short[] samples = new short[SharingNetworking.AUDIO_FRAME_SAMPLES * SharingNetworking.AUDIO_CHANNELS];
	// Capture timestamps of the packets currently queued, oldest first
	private final ArrayDeque<Long> queuedTimestamps = new ArrayDeque<>();
	private boolean closed = false;

	public RemoteAudioPlayer() {
		try {
			decoder = new OpusDecoder(SharingNetworking.AUDIO_SAMPLE_RATE, SharingNetworking.AUDIO_CHANNELS);
		} catch(OpusException e) {
			throw new IllegalStateException("Failed to create Opus decoder", e);
		}

		left = createSource();
		right = createSource();
	}

	private static int createSource() {
		int source = AL10.alGenSources();
		AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 2.0f);
		AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, (float) SharingServer.AUDIO_RANGE);
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0f);
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
		return source;
	}

	public void setPosition(Vec3 leftPos, Vec3 rightPos) {
		if(closed) return;
		AL10.alSource3f(left, AL10.AL_POSITION, (float) leftPos.x, (float) leftPos.y, (float) leftPos.z);
		AL10.alSource3f(right, AL10.AL_POSITION, (float) rightPos.x, (float) rightPos.y, (float) rightPos.z);
	}

	// One Opus packet (20 ms of stereo audio) with the owner's capture time
	public void queue(byte[] opus, long timestamp) {
		if(closed || opus.length == 0) return;

		recycleProcessed();
		if(AL10.alGetSourcei(left, AL10.AL_BUFFERS_QUEUED) >= MAX_QUEUED_BUFFERS) return;

		int count;
		try {
			count = decoder.decode(opus, 0, opus.length, samples, 0, SharingNetworking.AUDIO_FRAME_SAMPLES, false);
		} catch(OpusException e) {
			return;
		}
		if(count <= 0) return;

		queueChannel(left, count, 0);
		queueChannel(right, count, 1);
		queuedTimestamps.addLast(timestamp);

		Minecraft minecraft = Minecraft.getInstance();
		float volume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER) * minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
		AL10.alSourcef(left, AL10.AL_GAIN, volume);
		AL10.alSourcef(right, AL10.AL_GAIN, volume);

		// Restart both together after an underrun, once a small backlog exists
		if(!isPlaying() && AL10.alGetSourcei(left, AL10.AL_BUFFERS_QUEUED) >= START_BUFFERS) {
			try(MemoryStack stack = MemoryStack.stackPush()) {
				AL10.alSourcePlayv(stack.ints(left, right));
			}
		}
	}

	// Deinterleaves one channel of the decoded samples into a new buffer queued on source
	private void queueChannel(int source, int count, int channel) {
		ShortBuffer data = MemoryUtil.memAllocShort(count);
		for(int i = 0; i < count; i++) data.put(samples[i * SharingNetworking.AUDIO_CHANNELS + channel]);
		data.flip();

		int buffer = AL10.alGenBuffers();
		AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, SharingNetworking.AUDIO_SAMPLE_RATE);
		MemoryUtil.memFree(data);
		AL10.alSourceQueueBuffers(source, buffer);
	}

	private boolean isPlaying() {
		return AL10.alGetSourcei(left, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
	}

	/* The owner's capture time of the sample being heard right now, or -1 when not playing.
	 * Video frames with a timestamp up to this are due.
	 */
	public long playbackTimestamp() {
		if(closed || !isPlaying()) return -1;
		recycleProcessed();
		Long current = queuedTimestamps.peekFirst();
		if(current == null) return -1;

		int offset = AL10.alGetSourcei(left, AL11.AL_SAMPLE_OFFSET);
		return current + offset * 1000L / SharingNetworking.AUDIO_SAMPLE_RATE;
	}

	private void recycleProcessed() {
		int processed = Math.min(AL10.alGetSourcei(left, AL10.AL_BUFFERS_PROCESSED), AL10.alGetSourcei(right, AL10.AL_BUFFERS_PROCESSED));
		for(int i = 0; i < processed; i++) {
			AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(left));
			AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(right));
			queuedTimestamps.pollFirst();
		}
	}

	public void close() {
		if(closed) return;
		closed = true;
		try(MemoryStack stack = MemoryStack.stackPush()) {
			AL10.alSourceStopv(stack.ints(left, right));
		}
		// Stopped sources mark every buffer processed
		int queued = AL10.alGetSourcei(left, AL10.AL_BUFFERS_QUEUED);
		for(int i = 0; i < queued; i++) AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(left));
		queued = AL10.alGetSourcei(right, AL10.AL_BUFFERS_QUEUED);
		for(int i = 0; i < queued; i++) AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(right));
		AL10.alDeleteSources(left);
		AL10.alDeleteSources(right);
	}

}
