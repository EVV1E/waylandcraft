package dev.evvie.waylandcraft.sharing;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryUtil;

/* H.264 encoder backed by x264 in the native library (native/src/h264.rs).
 *
 * Output is baseline profile so the viewers' JCodec decoder can play it. Compared with
 * JCodec's own encoder on the same 848x480 content, frames are much smaller (a scrolling
 * terminal about 9 KB instead of 20 KB, typing about 2 KB instead of 8 KB, moving video a
 * few KB instead of about 24 KB) and encoding is far faster.
 *
 * Not thread-safe: use one instance from a single thread.
 */
public class NativeH264Encoder implements H264Codec.FrameEncoder {

	private final int width;
	private final int height;
	private long handle;

	public NativeH264Encoder(int width, int height, int qp) {
		this.width = width;
		this.height = height;
		this.handle = create(width, height, qp);
	}

	@Override
	public boolean matches(int width, int height) {
		return this.width == width && this.height == height;
	}

	// rgba must be a direct buffer of width * height tightly packed RGBA pixels, top row first
	@Override
	public byte[] encode(ByteBuffer rgba, boolean keyFrame) {
		if(!rgba.isDirect() || rgba.capacity() < width * height * 4) throw new IllegalArgumentException("Expected a direct RGBA buffer");
		return encode(handle, MemoryUtil.memAddress(rgba), keyFrame);
	}

	@Override
	public void close() {
		if(handle == 0) return;
		destroy(handle);
		handle = 0;
	}

	private static native long create(int width, int height, int qp);
	private static native byte[] encode(long handle, long rgbaPtr, boolean keyFrame);
	private static native void destroy(long handle);

}
