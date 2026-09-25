package dev.evvie.waylandcraft.sharing;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.encode.RateControl;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.logging.LogLevel;
import org.jcodec.common.logging.Logger;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Size;

import dev.evvie.waylandcraft.WaylandCraftCommon;

/* H.264 encoding for shared window video. Viewers decode with JCodec in VideoDecoder
 * (pure Java, so every client can decode). Owners encode with x264 from the native library when it's available
 * (NativeH264Encoder, much smaller frames), and with JCodec otherwise.
 *
 * JCodec, measured on 848x480 content: encoding takes 15-25 ms and decoding about 13 ms per frame.
 * Compared with the earlier motion JPEG, a keystroke in a terminal costs about 8 KB instead
 * of about 115 KB, scrolling about 20 KB instead of 120 KB, and moving video about 24 KB
 * instead of 42 KB.
 *
 * Colors use full-range BT.601 (JCodec's YUV420J). JCodec stores samples as signed bytes
 * (value - 128). This class must stay free of client-only classes: the server's test
 * pattern source uses it too.
 */
public class H264Codec {

	// Constant quantizer: steady quality, size follows content (idle deltas are tiny)
	static final int QP = 30;

	// Cleared the first time the native encoder can't be used (e.g. an older native library)
	private static boolean nativeAvailable = true;

	public interface FrameEncoder {
		boolean matches(int width, int height);
		// rgba: tightly packed RGBA rows, top row first. Key frames also carry SPS/PPS,
		// so a fresh decoder can start at any of them.
		byte[] encode(ByteBuffer rgba, boolean keyFrame);
		void close();
	}

	// Prefers x264; falls back to JCodec's encoder for good if the native one fails
	public static FrameEncoder createEncoder(int width, int height) {
		if(nativeAvailable) {
			try {
				return new NativeH264Encoder(width, height, QP);
			} catch(UnsatisfiedLinkError | RuntimeException e) {
				nativeAvailable = false;
				WaylandCraftCommon.LOGGER.warn("Native x264 encoder unavailable, using JCodec for window sharing: " + e);
			}
		}
		return new Encoder(width, height);
	}

	static {
		// JCodec logs decoder warnings to stdout by default
		Logger.setLevel(LogLevel.ERROR);
	}

	private static final RateControl CONSTANT_QP = new RateControl() {
		@Override
		public int startPicture(Size size, int maxSize, SliceType sliceType) {
			return QP;
		}

		@Override
		public int initialQpDelta() {
			return 0;
		}

		@Override
		public int accept(int bits) {
			return 0;
		}
	};

	public static class Encoder implements FrameEncoder {

		private final int width;
		private final int height;
		private final H264Encoder encoder = new H264Encoder(CONSTANT_QP);
		private final Picture picture;
		private final ByteBuffer out;

		public Encoder(int width, int height) {
			this.width = width;
			this.height = height;
			this.picture = Picture.create(width, height, ColorSpace.YUV420J);
			this.out = ByteBuffer.allocate(encoder.estimateBufferSize(picture));
		}

		@Override
		public boolean matches(int width, int height) {
			return this.width == width && this.height == height;
		}

		@Override
		public byte[] encode(ByteBuffer rgba, boolean keyFrame) {
			rgbaToYuv(rgba, picture);
			out.clear();
			ByteBuffer frame = keyFrame ? encoder.encodeIDRFrame(picture, out) : encoder.encodePFrame(picture, out);
			byte[] data = new byte[frame.remaining()];
			frame.get(data);
			return data;
		}

		@Override
		public void close() {
		}

	}

	private static void rgbaToYuv(ByteBuffer rgba, Picture picture) {
		int w = picture.getWidth();
		int h = picture.getHeight();
		byte[] yPlane = picture.getPlaneData(0);
		byte[] uPlane = picture.getPlaneData(1);
		byte[] vPlane = picture.getPlaneData(2);

		for(int i = 0; i < w * h; i++) {
			int r = rgba.get(i * 4) & 0xff;
			int g = rgba.get(i * 4 + 1) & 0xff;
			int b = rgba.get(i * 4 + 2) & 0xff;
			yPlane[i] = (byte) (((77 * r + 150 * g + 29 * b) >> 8) - 128);
		}

		// Chroma from the average of each 2x2 block
		int cw = w / 2;
		for(int y = 0; y < h / 2; y++) {
			for(int x = 0; x < cw; x++) {
				int r = 0, g = 0, b = 0;
				for(int dy = 0; dy < 2; dy++) {
					for(int dx = 0; dx < 2; dx++) {
						int i = ((2 * y + dy) * w + 2 * x + dx) * 4;
						r += rgba.get(i) & 0xff;
						g += rgba.get(i + 1) & 0xff;
						b += rgba.get(i + 2) & 0xff;
					}
				}
				r >>= 2;
				g >>= 2;
				b >>= 2;
				uPlane[y * cw + x] = (byte) ((-43 * r - 85 * g + 128 * b) >> 8);
				vPlane[y * cw + x] = (byte) ((128 * r - 107 * g - 21 * b) >> 8);
			}
		}
	}
	
}
