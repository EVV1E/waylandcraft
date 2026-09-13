package dev.evvie.waylandcraft.bridge.dmabuf;

public record Dmabuf(int width, int height, int format, long modifier, DmabufPlane[] planes) {
}
