package dev.evvie.waylandcraft.sharing;

import java.nio.ShortBuffer;

import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/* Streams a shared window's Opus audio through an OpenAL source placed at the window.
 * Minecraft makes its OpenAL context current for the whole process, so this runs on the
 * client thread next to the game's own sound engine. Mono buffers are spatialized by
 * OpenAL relative to the listener the game already positions.
 */
public class RemoteAudioPlayer {

	// Drop audio instead of building up latency when packets arrive faster than they play (20 ms each)
	private static final int MAX_QUEUED_BUFFERS = 15;

	private final int source;
	private final OpusDecoder decoder;
	private final short[] samples = new short[SharingNetworking.AUDIO_FRAME_SAMPLES];
	private boolean closed = false;

	public RemoteAudioPlayer() {
		try {
			decoder = new OpusDecoder(SharingNetworking.AUDIO_SAMPLE_RATE, 1);
		} catch(OpusException e) {
			throw new IllegalStateException("Failed to create Opus decoder", e);
		}

		source = AL10.alGenSources();
		AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 2.0f);
		AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, (float) SharingServer.AUDIO_RANGE);
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0f);
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
	}

	public void setPosition(Vec3 pos) {
		if(closed) return;
		AL10.alSource3f(source, AL10.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z);
	}

	// One Opus packet (20 ms of mono audio)
	public void queue(byte[] opus) {
		if(closed || opus.length == 0) return;

		recycleProcessed();
		if(AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) >= MAX_QUEUED_BUFFERS) return;

		int count;
		try {
			count = decoder.decode(opus, 0, opus.length, samples, 0, samples.length, false);
		} catch(OpusException e) {
			return;
		}
		if(count <= 0) return;

		int buffer = AL10.alGenBuffers();
		ShortBuffer data = MemoryUtil.memAllocShort(count);
		data.put(samples, 0, count).flip();
		AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, SharingNetworking.AUDIO_SAMPLE_RATE);
		MemoryUtil.memFree(data);
		AL10.alSourceQueueBuffers(source, buffer);

		Minecraft minecraft = Minecraft.getInstance();
		float volume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER) * minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
		AL10.alSourcef(source, AL10.AL_GAIN, volume);

		// Restart after an underrun; wait for a small backlog first to absorb jitter
		if(AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) >= 5) {
			AL10.alSourcePlay(source);
		}
	}

	private void recycleProcessed() {
		int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
		for(int i = 0; i < processed; i++) {
			AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(source));
		}
	}

	public void close() {
		if(closed) return;
		closed = true;
		AL10.alSourceStop(source);
		recycleProcessed();
		AL10.alDeleteSources(source);
	}

}
