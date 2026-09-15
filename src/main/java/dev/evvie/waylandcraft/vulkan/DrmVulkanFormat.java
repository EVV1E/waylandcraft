package dev.evvie.waylandcraft.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.lwjgl.vulkan.VK12;

public record DrmVulkanFormat(int fourcc, int vkFormat, boolean hasAlpha) {
	
	private static byte charToByte(char x) {
		return (byte) ((int) x);
	}
	
	private static int createFourcc(char c1, char c2, char c3, char c4) {
		int b1 = charToByte(c1);
		int b2 = charToByte(c2);
		int b3 = charToByte(c3);
		int b4 = charToByte(c4);
		return b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
	}
	
	public String getFourccString() {
		byte[] bytes = new byte[4];
		ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(fourcc);
		return new String(bytes, StandardCharsets.US_ASCII);
	}
	
	// [31:0] A:R:G:B 8:8:8:8 little endian
	public static final DrmVulkanFormat DRM_FORMAT_ARGB8888 = new DrmVulkanFormat(createFourcc('A', 'R', '2', '4'), VK12.VK_FORMAT_B8G8R8A8_UNORM, true);
	
	// [31:0] x:R:G:B 8:8:8:8 little endian
	public static final DrmVulkanFormat DRM_FORMAT_XRGB8888 = new DrmVulkanFormat(createFourcc('X', 'R', '2', '4'), VK12.VK_FORMAT_B8G8R8A8_UNORM, false);
	
	// [31:0] A:B:G:R 8:8:8:8 little endian
	public static final DrmVulkanFormat DRM_FORMAT_ABGR8888 = new DrmVulkanFormat(createFourcc('A', 'B', '2', '4'), VK12.VK_FORMAT_R8G8B8A8_UNORM, true);
	
	// [31:0] x:B:G:R 8:8:8:8 little endian
	public static final DrmVulkanFormat DRM_FORMAT_XBGR8888 = new DrmVulkanFormat(createFourcc('X', 'B', '2', '4'), VK12.VK_FORMAT_R8G8B8A8_UNORM, false);
	
	// [7:0] R
	public static final DrmVulkanFormat DRM_FORMAT_R8 = new DrmVulkanFormat(createFourcc('R', '8', ' ', ' '), VK12.VK_FORMAT_R8_UNORM, false);
	
	// [23:0] R:G:B little endian
	public static final DrmVulkanFormat DRM_FORMAT_RGB888 = new DrmVulkanFormat(createFourcc('R', 'G', '2', '4'), VK12.VK_FORMAT_B8G8R8_UNORM, false);
	
	// [23:0] B:G:R little endian
	public static final DrmVulkanFormat DRM_FORMAT_BGR888 = new DrmVulkanFormat(createFourcc('B', 'G', '2', '4'), VK12.VK_FORMAT_R8G8B8_UNORM, false);
	
	public static final DrmVulkanFormat[] FORMATS = new DrmVulkanFormat[] {
			DRM_FORMAT_ARGB8888,
			DRM_FORMAT_XRGB8888,
			DRM_FORMAT_ABGR8888,
			DRM_FORMAT_XBGR8888,
			DRM_FORMAT_R8,
			DRM_FORMAT_RGB888,
			DRM_FORMAT_BGR888
	};
	
}
