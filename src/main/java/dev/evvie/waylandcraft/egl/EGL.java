package dev.evvie.waylandcraft.egl;

import java.io.ByteArrayOutputStream;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeEGL;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;

public class EGL {
	
	private static long getProcAddress(String name) {
		return GLFW.glfwGetProcAddress(name);
	}
	
	// Random (non-) EGL constants
	public static final long NULL = 0L;
	public static final int EGL_TRUE = 1;
	public static final int EGL_FALSE = 0;
	public static final long EGL_NONE = 0x3038;
	public static final long EGL_NO_CONTEXT = 0L;
	public static final long EGL_NO_IMAGE = 0L;
	public static final long EGL_WIDTH = 0x3057;
	public static final long EGL_HEIGHT = 0x3056;
	public static final long EGL_LINUX_DMA_BUF_EXT = 0x3270;
	public static final long EGL_LINUX_DRM_FOURCC_EXT = 0x3271;
	public static final int EGL_DEVICE_EXT = 0x322C;
	public static final int EGL_DRM_RENDER_NODE_FILE_EXT = 0x3377;
	
	public static final long EGL_DMA_BUF_PLANE0_FD_EXT = 0x3272;
	public static final long EGL_DMA_BUF_PLANE0_OFFSET_EXT = 0x3273;
	public static final long EGL_DMA_BUF_PLANE0_PITCH_EXT = 0x3274;
	public static final long EGL_DMA_BUF_PLANE1_FD_EXT = 0x3275;
	public static final long EGL_DMA_BUF_PLANE1_OFFSET_EXT = 0x3276;
	public static final long EGL_DMA_BUF_PLANE1_PITCH_EXT = 0x3277;
	public static final long EGL_DMA_BUF_PLANE2_FD_EXT = 0x3278;
	public static final long EGL_DMA_BUF_PLANE2_OFFSET_EXT = 0x3279;
	public static final long EGL_DMA_BUF_PLANE2_PITCH_EXT = 0x327A;
	public static final long EGL_DMA_BUF_PLANE3_FD_EXT = 0x3440;
	public static final long EGL_DMA_BUF_PLANE3_OFFSET_EXT = 0x3441;
	public static final long EGL_DMA_BUF_PLANE3_PITCH_EXT = 0x3442;
	
	public static final long EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT = 0x3443;
	public static final long EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT = 0x3444;
	public static final long EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT = 0x3445;
	public static final long EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT = 0x3446;
	public static final long EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT = 0x3447;
	public static final long EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT = 0x3448;
	public static final long EGL_DMA_BUF_PLANE3_MODIFIER_LO_EXT = 0x3449;
	public static final long EGL_DMA_BUF_PLANE3_MODIFIER_HI_EXT = 0x344A;
	
	// EGL error codes
	public static final int EGL_SUCCESS = 0x3000;
	public static final int EGL_NOT_INITIALIZED = 0x3001;
	public static final int EGL_BAD_ACCESS = 0x3002;
	public static final int EGL_BAD_ALLOC = 0x3003;
	public static final int EGL_BAD_ATTRIBUTE = 0x3004;
	public static final int EGL_BAD_CONFIG = 0x3005;
	public static final int EGL_BAD_CONTEXT = 0x3006;
	public static final int EGL_BAD_CURRENT_SURFACE = 0x3007;
	public static final int EGL_BAD_DISPLAY = 0x3008;
	public static final int EGL_BAD_MATCH = 0x3009;
	public static final int EGL_BAD_NATIVE_PIXMAP = 0x300A;
	public static final int EGL_BAD_NATIVE_WINDOW = 0x300B;
	public static final int EGL_BAD_PARAMETER = 0x300C;
	public static final int EGL_BAD_SURFACE = 0x300D;
	public static final int EGL_CONTEXT_LOST = 0x300E;
	
	// EGL Function pointers
	private static long procQueryDisplayAttribEXT;
	private static long procQueryDeviceStringEXT;
	private static long procCreateImage;
	private static long procDestroyImage;
	private static long procGetError;
	private static long procQueryDmaBufFormatsEXT;
	private static long procQueryDmaBufModifiersEXT;
	
	static {
		procQueryDisplayAttribEXT = getProcAddress("eglQueryDisplayAttribEXT");
		procQueryDeviceStringEXT = getProcAddress("eglQueryDeviceStringEXT");
		procCreateImage = getProcAddress("eglCreateImage");
		procDestroyImage = getProcAddress("eglDestroyImage");
		procGetError = getProcAddress("eglGetError");
		procQueryDmaBufFormatsEXT = getProcAddress("eglQueryDmaBufFormatsEXT");
		procQueryDmaBufModifiersEXT = getProcAddress("eglQueryDmaBufModifiersEXT");
	}
	
	private static long memAddressSafe(PointerBuffer buffer) {
		return buffer == null ? NULL : buffer.address();
	}
	
	private static String readNTBytesToStringUTF8(long address) {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		
		byte b;
		while((b = MemoryUtil.memGetByte(address)) != 0) {
			stream.write(b);
			address++;
		}
		
		byte[] data = stream.toByteArray();
		return new String(data, StandardCharsets.UTF_8);
	}
	
	public static long getEGLDisplay() {
		return GLFWNativeEGL.glfwGetEGLDisplay();
	}
	
	public static int eglGetError() {
		return JNI.invokeI(procGetError);
	}
	
	public static String eglGetErrorString() {
		switch(eglGetError()) {
		case EGL_SUCCESS: return "EGL_SUCCESS";
		case EGL_NOT_INITIALIZED: return "EGL_NOT_INITIALIZED";
		case EGL_BAD_ACCESS: return "EGL_BAD_ACCESS";
		case EGL_BAD_ALLOC: return "EGL_BAD_ALLOC";
		case EGL_BAD_ATTRIBUTE: return "EGL_BAD_ATTRIBUTE";
		case EGL_BAD_CONFIG: return "EGL_BAD_CONFIG";
		case EGL_BAD_CONTEXT: return "EGL_BAD_CONTEXT";
		case EGL_BAD_CURRENT_SURFACE: return "EGL_BAD_CURRENT_SURFACE";
		case EGL_BAD_DISPLAY: return "EGL_BAD_DISPLAY";
		case EGL_BAD_MATCH: return "EGL_BAD_MATCH";
		case EGL_BAD_NATIVE_PIXMAP: return "EGL_BAD_NATIVE_PIXMAP";
		case EGL_BAD_NATIVE_WINDOW: return "EGL_BAD_NATIVE_WINDOW";
		case EGL_BAD_PARAMETER: return "EGL_BAD_PARAMETER";
		case EGL_BAD_SURFACE: return "EGL_BAD_SURFACE";
		case EGL_CONTEXT_LOST: return "EGL_CONTEXT_LOST";
		default: return "<unknown EGL error code>";
		}
	}
	
	public static long eglCreateImage(long dpy, long ctx, int target, long buffer, PointerBuffer attrib_list) {
		return neglCreateImage(dpy, ctx, target, buffer, memAddressSafe(attrib_list));
	}
	
	public static long neglCreateImage(long dpy, long ctx, int target, long buffer, long attrib_list) {
		// EGLImage eglCreateImage (EGLDisplay dpy, EGLContext ctx, EGLenum target, EGLClientBuffer buffer, const EGLAttrib *attrib_list);
		return JNI.invokePPPPP(dpy, ctx, target, buffer, attrib_list, procCreateImage);
	}
	
	public static boolean eglDestroyImage(long dpy, long image) {
		return neglDestroyImage(dpy, image) == EGL_TRUE;
	}
	
	public static int neglDestroyImage(long dpy, long image) {
		// EGLBoolean eglDestroyImage (EGLDisplay dpy, EGLImage image);
		return JNI.invokePPI(dpy, image, procDestroyImage);
	}
	
	public static String eglQueryDeviceStringEXT(long device, int name) {
		long ptr = neglQueryDeviceStringEXT(device, name);
		return ptr == NULL ? null : readNTBytesToStringUTF8(ptr);
	}
	
	public static long neglQueryDeviceStringEXT(long device, int name) {
		// const char *eglQueryDeviceStringEXT(EGLDeviceEXT device, EGLint name);
		return JNI.invokePP(device, name, procQueryDeviceStringEXT);
	}
	
	public static boolean eglQueryDisplayAttribEXT(long dpy, int attribute, PointerBuffer value) {
		return neglQueryDisplayAttribEXT(dpy, attribute, memAddressSafe(value)) == EGL_TRUE;
	}
	
	public static int neglQueryDisplayAttribEXT(long dpy, int attribute, long value) {
		// EGLBoolean eglQueryDisplayAttribEXT(EGLDisplay dpy, EGLint attribute, EGLAttrib *value);
		return JNI.invokePPI(dpy, attribute, value, procQueryDisplayAttribEXT);
	}
	
	public static boolean eglQueryDmaBufFormatsEXT(long dpy, int max_formats, IntBuffer formats, IntBuffer num_formats) {
		return neglQueryDmaBufFormatsEXT(dpy, max_formats, MemoryUtil.memAddressSafe(formats), MemoryUtil.memAddressSafe(num_formats)) == EGL_TRUE;
	}
	
	public static int neglQueryDmaBufFormatsEXT(long dpy, int max_formats, long formats, long num_formats) {
		// EGLBoolean eglQueryDmaBufFormatsEXT (EGLDisplay dpy, EGLint max_formats, EGLint *formats, EGLint *num_formats);
		return JNI.invokePPPI(dpy, max_formats, formats, num_formats, procQueryDmaBufFormatsEXT);
	}
	
	public static boolean eglQueryDmaBufModifiersEXT(long dpy, int format, int max_modifiers, LongBuffer modifiers, IntBuffer external_only, IntBuffer num_modifiers) {
		return neglQueryDmaBufModifiersEXT(dpy, format, max_modifiers, MemoryUtil.memAddressSafe(modifiers), MemoryUtil.memAddressSafe(external_only), MemoryUtil.memAddressSafe(num_modifiers)) == EGL_TRUE;
	}
	
	public static int neglQueryDmaBufModifiersEXT(long dpy, int format, int max_modifiers, long modifiers, long external_only, long num_modifiers) {
		// EGLBoolean eglQueryDmaBufModifiersEXT (EGLDisplay dpy, EGLint format, EGLint max_modifiers, EGLuint64KHR *modifiers, EGLBoolean *external_only, EGLint *num_modifiers);
		return JNI.invokePPPPI(dpy, format, max_modifiers, modifiers, external_only, num_modifiers, procQueryDmaBufModifiersEXT);
	}
	
}
