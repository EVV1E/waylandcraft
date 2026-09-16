package dev.evvie.waylandcraft.bridge.dmabuf;

public record DmabufPlane(int fd, long size, int offset, int stride) {
}
