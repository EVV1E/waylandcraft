package dev.evvie.waylandcraft.sharing;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.common.logging.LogLevel;
import org.jcodec.common.logging.Logger;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import com.mojang.blaze3d.platform.NativeImage;

/* Client-side H.264 decoding of shared window video, through JCodec (pure Java, so
 * every client can decode). Frames come from x264 or JCodec encoders, always baseline
 * profile. Colors are full-range BT.601, matching H264Codec's encoders.
 */
public class VideoDecoder {

	static {
		// JCodec logs decoder warnings to stdout by default
		Logger.setLevel(LogLevel.ERROR);
	}

	private final H264Decoder decoder = new H264Decoder();
	private byte[][] buffer = null;
	private int bufferWidth = 0;
	private int bufferHeight = 0;

	// Returns null if the frame couldn't be decoded. width/height are the frame's
	// size, sent alongside it, so the decode buffer can be sized up front.
	public NativeImage decode(byte[] data, int width, int height) {
		if(buffer == null || bufferWidth != width || bufferHeight != height) {
			// Room for macroblock padding
			buffer = Picture.create(width + 16, height + 16, ColorSpace.YUV420J).getData();
			bufferWidth = width;
			bufferHeight = height;
		}

		Picture picture;
		try {
			picture = decoder.decodeFrame(ByteBuffer.wrap(data), buffer);
		} catch(RuntimeException e) {
			return null;
		}
		if(picture == null) return null;
		return yuvToImage(picture);
	}

	private static NativeImage yuvToImage(Picture picture) {
		int w = picture.getCroppedWidth();
		int h = picture.getCroppedHeight();
		int stride = picture.getWidth();
		int chromaStride = stride / 2;
		byte[] yPlane = picture.getPlaneData(0);
		byte[] uPlane = picture.getPlaneData(1);
		byte[] vPlane = picture.getPlaneData(2);

		NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) {
				int luma = yPlane[y * stride + x] + 128;
				int u = uPlane[(y / 2) * chromaStride + x / 2];
				int v = vPlane[(y / 2) * chromaStride + x / 2];
				int r = clamp(luma + ((359 * v) >> 8));
				int g = clamp(luma - ((88 * u + 183 * v) >> 8));
				int b = clamp(luma + ((454 * u) >> 8));
				// NativeImage pixels are ABGR
				image.setPixelRGBA(x, y, 0xff000000 | b << 16 | g << 8 | r);
			}
		}
		return image;
	}

	private static int clamp(int value) {
		return value < 0 ? 0 : (value > 255 ? 255 : value);
	}

	
}
