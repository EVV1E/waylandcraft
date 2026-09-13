package dev.evvie.waylandcraft.egl;

import java.io.ByteArrayOutputStream;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;

public class EGL {
	
	private static long getProcAddress(String name) {
		return GLFW.glfwGetProcAddress(name);
	}
	
	// Random (non-) EGL constants
	public static final long NULL = 0L;
	public static final int EGL_TRUE = 1;
	public static final int EGL_DRM_RENDER_NODE_FILE_EXT = 0x3377;
	public static final int EGL_DEVICE_EXT = 0x322C;
	
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
