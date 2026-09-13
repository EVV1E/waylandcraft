package dev.evvie.waylandcraft.bridge.dmabuf;

public record DmabufFormat(int code, long modifier) {
	
	public static final long MODIFIER_LINEAR = 0L;
	public static final long MODIFIER_INVALID = 0xffffffffffffffL;
	
}
