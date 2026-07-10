package dev.evvie.waylandcraft.datasync;

public record ImagePatch(short format, int x, int y, int width, int height, byte[] data) {
	
	public static final short FORMAT_RAW_RGBA = 1;
	public static final short FORMAT_RAW_RGB = 2;
	
}
