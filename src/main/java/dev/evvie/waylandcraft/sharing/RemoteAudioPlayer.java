package dev.evvie.waylandcraft.sharing;

import java.nio.ByteBuffer;

import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/* Streams a shared window's PCM audio through an OpenAL source placed at its item frame.
 * Minecraft makes its OpenAL context current for the whole process, so this runs on the
 * client thread next to the game's own sound engine. Mono buffers are spatialized by
 * OpenAL relative to the listener the game already positions.
 */
public class RemoteAudioPlayer {

	// Drop audio instead of building up latency when chunks arrive faster than they play
	private static final int MAX_QUEUED_BUFFERS = 10;

	private final int source;
	private boolean closed = false;

	public RemoteAudioPlayer() {
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

	// Signed 16-bit little-endian mono PCM
	public void queue(byte[] pcm) {
		if(closed || pcm.length < 2) return;

		recycleProcessed();
		if(AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) >= MAX_QUEUED_BUFFERS) return;

		int buffer = AL10.alGenBuffers();
		ByteBuffer data = MemoryUtil.memAlloc(pcm.length & ~1);
		data.put(pcm, 0, pcm.length & ~1).flip();
		AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, SharingNetworking.AUDIO_SAMPLE_RATE);
		MemoryUtil.memFree(data);
		AL10.alSourceQueueBuffers(source, buffer);

		Minecraft minecraft = Minecraft.getInstance();
		float volume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER) * minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
		AL10.alSourcef(source, AL10.AL_GAIN, volume);

		// Restart after an underrun; wait for a small backlog first to absorb jitter
		if(AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) >= 3) {
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
