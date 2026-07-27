package dev.evvie.waylandcraft.gpu;

import org.lwjgl.glfw.GLFW;

import dev.evvie.waylandcraft.WaylandCraftCommon;

/**
 * Probes, once, whether GLFW can actually create an OpenGL context via EGL
 * on this system. Some NVIDIA PRIME/Optimus offload configurations fail
 * EGL context creation entirely (GLFW_ERROR 0x10008), which would otherwise
 * take down the whole OpenGL backend if we unconditionally forced EGL for
 * DMA-BUF/EGLImage compositor integration.
 */
public final class EglAvailability {

	private static Boolean available = null;

	private EglAvailability() {}

	public static boolean probe() {
		if(available == null) {
			available = runProbe();
			if(!available) {
				WaylandCraftCommon.LOGGER.warn("[waylandcraft/gpu] EGL context creation is unavailable on this system (NVIDIA PRIME/Optimus offload is a common cause) — falling back to native GL context creation. DMA-BUF/EGLImage compositor integration will be disabled.");
			}
		}
		return available;
	}

	private static boolean runProbe() {
		if(!GLFW.glfwInit()) return false;

		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_EGL_CONTEXT_API);

		long handle = GLFW.glfwCreateWindow(1, 1, "waylandcraft-egl-probe", 0L, 0L);
		GLFW.glfwDefaultWindowHints();

		if(handle == 0L) return false;

		GLFW.glfwDestroyWindow(handle);
		return true;
	}

}
