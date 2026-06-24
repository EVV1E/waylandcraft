package dev.evvie.waylandcraft.vulkanmod;

import dev.evvie.waylandcraft.WaylandCraftCommon;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWayland;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

/**
 * Manages an EGL display and function-address resolution for use when
 * VulkanMod is active (GLFW_NO_API — GLFW never loads libEGL itself).
 *
 * Also owns a minimal no-config/surfaceless EGL context used exclusively for
 * eglDestroyImage calls (see destroyImage()).
 */
public final class EglHelper {

    private static volatile long eglDisplay = 0L;
    private static volatile SharedLibrary eglLib = null;

    // ── Cleanup context ────────────────────────────────────────────────────────
    // eglDestroyImage requires a current EGL context even for DMA-BUF images.
    // NVIDIA 595 driver: internally calls eglGetCurrentContext() which must be
    // non-null, otherwise it dereferences NULL+0x40 and SIGSEGVs on its own
    // background thread when the DMA-BUF fd that backs the EGLImage is closed.
    //
    // We lazily create a minimal context via:
    //   EGL_KHR_no_config_context  (EGL_NO_CONFIG_KHR = 0 as config)
    //   EGL_KHR_surfaceless_context (EGL_NO_SURFACE as draw/read surfaces)
    // Both have been supported by NVIDIA since driver 460+.
    private static volatile long cleanupContext = 0L;  // 0 = not created, -1 = failed
    private static volatile long fnMakeCurrent  = 0L;
    private static volatile long fnDestroyImage = 0L;
    private static final    long EGL_NONE = 0x3038L;   // attrib-list terminator

    private EglHelper() {}

    // ── libEGL loading ─────────────────────────────────────────────────────────

    /** Load or return the cached libEGL, or null on failure. */
    private static SharedLibrary ensureLib() {
        if (eglLib != null) return eglLib;
        synchronized (EglHelper.class) {
            if (eglLib != null) return eglLib;
            for (String n : new String[]{"EGL", "libEGL.so.1", "libEGL.so"}) {
                try {
                    SharedLibrary lib = Library.loadNative(EglHelper.class, null, n);
                    if (lib != null) {
                        LOGGER.info("[waylandcraft/vulkanmod] libEGL loaded");
                        eglLib = lib;
                        return lib;
                    }
                } catch (Exception ignored) {}
            }
            LOGGER.error("[waylandcraft/vulkanmod] Could not load libEGL");
        }
        return null;
    }

    /** Resolve a named EGL symbol — tries GLFW first, then dlsym, then eglGetProcAddress. */
    private static long resolveEglFunc(String name) {
        long addr = GLFW.glfwGetProcAddress(name);
        if (addr != 0L) return addr;
        SharedLibrary lib = ensureLib();
        if (lib == null) return 0L;
        addr = lib.getFunctionAddress(name);
        if (addr != 0L) return addr;
        // Fall through to eglGetProcAddress for extension symbols
        long getProcAddr = lib.getFunctionAddress("eglGetProcAddress");
        if (getProcAddr == 0L) return 0L;
        return getProc(getProcAddr, name);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Call eglGetProcAddress(funcProcAddr, name) to look up an EGL extension.
     */
    public static long getProc(long eglGetProcAddress, String name) {
        if (eglGetProcAddress == 0L) return 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long namePtr = MemoryUtil.memAddress(stack.ASCII(name, true));
            return JNI.invokePP(namePtr, eglGetProcAddress);
        }
    }

    /** Convenience: look up an EGL extension symbol using our libEGL's eglGetProcAddress. */
    public static long getProc(String name) {
        return getProc(getEglGetProcAddress(), name);
    }

    /**
     * Returns a valid EGL display, creating one from the GLFW native display
     * if GLFW didn't already provide one (i.e. under VulkanMod's GLFW_NO_API).
     */
    public static long getOrCreate(long fromGlfw) {
        if (fromGlfw != 0L) return fromGlfw;
        if (eglDisplay != 0L) return eglDisplay;

        try {
            long eglGetDisplayFn = resolveEglFunc("eglGetDisplay");
            if (eglGetDisplayFn == 0L) {
                LOGGER.error("[waylandcraft/vulkanmod] eglGetDisplay not found");
                return 0L;
            }

            long nativeDisplay = GLFWNativeWayland.glfwGetWaylandDisplay();
            String kind = "wl_display";
            if (nativeDisplay == 0L) {
                nativeDisplay = GLFWNativeX11.glfwGetX11Display();
                kind = "X11 Display";
            }
            if (nativeDisplay == 0L) {
                kind = "EGL_DEFAULT_DISPLAY";
            }

            long display = JNI.invokePP(nativeDisplay, eglGetDisplayFn);
            if (display == 0L) {
                LOGGER.error("[waylandcraft/vulkanmod] eglGetDisplay({}) returned EGL_NO_DISPLAY", kind);
                return 0L;
            }

            long eglInitFn = resolveEglFunc("eglInitialize");
            if (eglInitFn != 0L) {
                int ok = JNI.invokePPPI(display, 0L, 0L, eglInitFn);
                if (ok == 0) LOGGER.warn("[waylandcraft/vulkanmod] eglInitialize returned EGL_FALSE");
            }

            eglDisplay = display;
            LOGGER.info("[waylandcraft/vulkanmod] EGL display 0x{} created from {}",
                        Long.toHexString(display), kind);
        } catch (Exception e) {
            LOGGER.error("[waylandcraft/vulkanmod] EGL setup failed", e);
        }
        return eglDisplay;
    }

    /** Returns the function pointer for eglGetProcAddress from libEGL, or 0. */
    public static long getEglGetProcAddress() {
        SharedLibrary lib = ensureLib();
        return lib != null ? lib.getFunctionAddress("eglGetProcAddress") : 0L;
    }

    /**
     * Returns the cached EGL display handle, or 0 if EGL was never initialised.
     * Callers that receive 0 must treat the EGL operation as a no-op —
     * NVIDIA's driver will SIGSEGV on a null display handle.
     */
    public static long get() {
        if (eglDisplay == 0L) {
            LOGGER.warn("[waylandcraft/vulkanmod] EglHelper.get() called before init or after shutdown — returning 0");
        }
        return eglDisplay;
    }

    // ── Cleanup context management ─────────────────────────────────────────────

    /**
     * Create the minimal no-config/surfaceless EGL context used for
     * eglDestroyImage calls.  Called at most once; subsequent calls are no-ops.
     *
     * Requires EGL_KHR_no_config_context (EGL_NO_CONFIG_KHR = 0 as config) and
     * EGL_KHR_surfaceless_context (EGL_NO_SURFACE for draw/read), both
     * supported by NVIDIA 460+ and Mesa.
     */
    public static synchronized void ensureCleanupContext() {
        if (cleanupContext != 0L) return;   // already created (or -1 = failed)

        long display = eglDisplay;
        if (display == 0L) return;

        fnMakeCurrent  = resolveEglFunc("eglMakeCurrent");
        fnDestroyImage = resolveEglFunc("eglDestroyImage");
        long fnCreate  = resolveEglFunc("eglCreateContext");

        if (fnMakeCurrent == 0L || fnDestroyImage == 0L || fnCreate == 0L) {
            LOGGER.warn("[waylandcraft/vulkanmod] EGL cleanup context unavailable — some functions not resolved");
            cleanupContext = -1L;
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // attribs = { EGL_NONE } — no specific API version requested;
            // EGL_KHR_no_config_context accepts this with config = 0
            long attribsPtr = MemoryUtil.memAddress(stack.ints((int) EGL_NONE));
            // eglCreateContext(display, EGL_NO_CONFIG_KHR=0, EGL_NO_CONTEXT=0, attribs)
            long ctx = JNI.invokePPPPP(display, 0L, 0L, attribsPtr, fnCreate);
            if (ctx == 0L) {
                LOGGER.warn("[waylandcraft/vulkanmod] eglCreateContext(EGL_NO_CONFIG_KHR) returned EGL_NO_CONTEXT " +
                            "— eglDestroyImage will be skipped (possible NVIDIA background crash)");
                cleanupContext = -1L;
                return;
            }
            cleanupContext = ctx;
            LOGGER.info("[waylandcraft/vulkanmod] EGL cleanup context 0x{} created", Long.toHexString(ctx));
        }
    }

    /**
     * Properly destroy an EGLImage by briefly binding the cleanup context,
     * calling eglDestroyImage, then releasing the context.
     *
     * <p>This MUST be called before the underlying DMA-BUF file descriptor is
     * closed.  If the EGLImage outlives the fd, NVIDIA's EGL background thread
     * detects the stale reference and SIGSEGVs trying to clean it up (it
     * dereferences the current EGL context which is NULL on our Vulkan thread).
     *
     * <p>Thread-safe: synchronized to prevent concurrent make-current races.
     */
    public static synchronized void destroyImage(long display, long image) {
        if (display == 0L || image == 0L) return;

        ensureCleanupContext();
        if (cleanupContext <= 0L || fnMakeCurrent == 0L || fnDestroyImage == 0L) {
            // Context creation failed — skip; the image leaks until eglTerminate.
            return;
        }

        // eglMakeCurrent(display, EGL_NO_SURFACE=0, EGL_NO_SURFACE=0, cleanupContext)
        JNI.invokePPPPI(display, 0L, 0L, cleanupContext, fnMakeCurrent);
        // eglDestroyImage(display, image)
        JNI.invokePPI(display, image, fnDestroyImage);
        // eglMakeCurrent(display, EGL_NO_SURFACE=0, EGL_NO_SURFACE=0, EGL_NO_CONTEXT=0)
        JNI.invokePPPPI(display, 0L, 0L, 0L, fnMakeCurrent);
    }

    // ── Shutdown ───────────────────────────────────────────────────────────────

    /**
     * Terminate the EGL display cleanly during JVM shutdown.
     *
     * <p>Called from {@code WaylandCraftBridge.shutdownHook()} and from
     * {@code MinecraftMixin.vulkanmod$onClose()} as an earlier-firing safety net.
     * Shutdown hooks run before native libraries are unloaded, so libEGL is
     * still valid here.  By calling {@code eglTerminate} with a current context
     * we allow NVIDIA's EGL background thread to flush its state gracefully
     * instead of dereferencing NULL+0x40 when it finds the display torn down
     * from underneath it.
     */
    public static synchronized void shutdown() {
        long display = eglDisplay;
        if (display == 0L) return;
        eglDisplay = 0L; // prevent any further EGL calls from this mod

        ensureCleanupContext();

        long fnTerminate    = resolveEglFunc("eglTerminate");
        long fnReleaseThread = resolveEglFunc("eglReleaseThread");

        // Bind cleanup context so NVIDIA's driver has a valid current context
        // while processing eglTerminate's internal cleanup callbacks.
        if (cleanupContext > 0L && fnMakeCurrent != 0L) {
            JNI.invokePPPPI(display, 0L, 0L, cleanupContext, fnMakeCurrent);
        }

        // eglTerminate(display) — releases all display resources and signals
        // NVIDIA's background thread to stop.
        if (fnTerminate != 0L) {
            JNI.invokePI(display, fnTerminate);
            LOGGER.info("[waylandcraft/vulkanmod] eglTerminate called — EGL display released cleanly");
        }

        // eglReleaseThread() — releases EGL per-thread state on this thread.
        if (fnReleaseThread != 0L) {
            JNI.invokeI(fnReleaseThread);
        }

        cleanupContext = -1L; // mark as gone
    }

    private static final org.slf4j.Logger LOGGER = WaylandCraftCommon.LOGGER;
}
