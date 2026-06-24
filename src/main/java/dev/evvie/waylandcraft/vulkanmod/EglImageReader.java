package dev.evvie.waylandcraft.vulkanmod;

import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Reads pixel data from an EGLImage (DMA-BUF backed) using a dedicated
 * GLES3 worker thread.
 *
 * ── WHY A WORKER THREAD ────────────────────────────────────────────────────
 * NVIDIA's Vulkan and GLES drivers share process state. eglMakeCurrent on the
 * Vulkan render thread corrupts GLES driver structs → crash.  Dedicated
 * daemon thread keeps GLES completely separate from Vulkan.
 *
 * ── WHY EXTERNAL_OES + DRAW CALL ──────────────────────────────────────────
 * DMA-BUF backed EGLImages require GL_TEXTURE_EXTERNAL_OES in GLES3.
 * Importing with GL_TEXTURE_2D silently produces an empty texture (all zeros).
 * GL_TEXTURE_EXTERNAL_OES textures cannot be FBO attachments, so the
 * draw-call approach is the only way to get pixels into a readable RGBA8 FBO.
 *
 * ── WHY VAO ────────────────────────────────────────────────────────────────
 * GLES3 core removed the default VAO (VAO id=0 is invalid).
 * glEnableVertexAttribArray / glVertexAttribPointer / glDrawArrays crash with
 * a NULL dereference in the driver's vertex-array state table if no VAO is
 * bound.  The VAO must be created BEFORE the VBO / attrib setup.
 */
public final class EglImageReader {

    private EglImageReader() {}

    // ── Dedicated readback thread ──────────────────────────────────────────────

    private record ReadbackJob(long display, long eglImage, int w, int h,
                               CompletableFuture<Long> result) {}

    private static final LinkedBlockingQueue<ReadbackJob> QUEUE =
        new LinkedBlockingQueue<>();

    private static final Thread WORKER;
    static {
        WORKER = new Thread(EglImageReader::workerLoop, "waylandcraft-vulkanmod-egl-readback");
        WORKER.setDaemon(true);
        WORKER.start();
    }

    /** Call before eglTerminate to drain the worker thread cleanly. */
    public static void shutdown() {
        WORKER.interrupt();
        try { WORKER.join(500); } catch (InterruptedException ignored) {}
    }

    private static void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            ReadbackJob job;
            try { job = QUEUE.take(); } catch (InterruptedException e) { break; }
            if (!ensureInit(job.display())) {
                job.result().complete(0L);
                continue;
            }
            job.result().complete(doReadPixels(job.eglImage(), job.w(), job.h()));
        }
    }

    /** Caller must free returned buffer with {@code MemoryUtil.nmemFree()}. Returns 0 on failure. */
    public static long readPixels(long display, long eglImage, int w, int h) {
        if (display == 0L || eglImage == 0L || w <= 0 || h <= 0) return 0L;
        var future = new CompletableFuture<Long>();
        QUEUE.offer(new ReadbackJob(display, eglImage, w, h, future));
        try {
            Long r = future.get(2, TimeUnit.SECONDS);
            return r != null ? r : 0L;
        } catch (Exception e) {
            LOG.error("[waylandcraft/vulkanmod/EglImageReader] readPixels timed out / failed", e);
            return 0L;
        }
    }

    // ── EGL constants ──────────────────────────────────────────────────────────
    private static final int EGL_SURFACE_TYPE           = 0x3033;
    private static final int EGL_PBUFFER_BIT            = 0x0001;
    private static final int EGL_RENDERABLE_TYPE        = 0x3040;
    private static final int EGL_OPENGL_ES3_BIT         = 0x0040;
    private static final int EGL_NONE                   = 0x3038;
    private static final int EGL_WIDTH                  = 0x3057;
    private static final int EGL_HEIGHT                 = 0x3056;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES_API          = 0x30A0;

    // ── GL constants ───────────────────────────────────────────────────────────
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    private static final int GL_TEXTURE_2D           = 0x0DE1;
    private static final int GL_TEXTURE_MIN_FILTER   = 0x2801;
    private static final int GL_TEXTURE_MAG_FILTER   = 0x2800;
    private static final int GL_NEAREST              = 0x2600;
    private static final int GL_CLAMP_TO_EDGE        = 0x812F;
    private static final int GL_TEXTURE_WRAP_S       = 0x2802;
    private static final int GL_TEXTURE_WRAP_T       = 0x2803;
    private static final int GL_FRAMEBUFFER          = 0x8D40;
    private static final int GL_COLOR_ATTACHMENT0    = 0x8CE0;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_RENDERBUFFER         = 0x8D41;
    private static final int GL_RGBA8                = 0x8058;
    private static final int GL_RGBA                 = 0x1908;
    private static final int GL_UNSIGNED_BYTE        = 0x1401;
    private static final int GL_TRIANGLE_STRIP       = 0x0005;
    private static final int GL_FLOAT               = 0x1406;
    private static final int GL_ARRAY_BUFFER         = 0x8892;
    private static final int GL_STATIC_DRAW          = 0x88B4;
    private static final int GL_FRAGMENT_SHADER      = 0x8B30;
    private static final int GL_VERTEX_SHADER        = 0x8B31;
    private static final int GL_COMPILE_STATUS       = 0x8B81;
    private static final int GL_LINK_STATUS          = 0x8B82;
    private static final int GL_TRUE                 = 1;

    // ── FFM helpers ────────────────────────────────────────────────────────────
    private static final Linker LINKER = Linker.nativeLinker();

    private static MemorySegment fn(long a)  { return MemorySegment.ofAddress(a); }
    private static MemorySegment ptr(long a) { return MemorySegment.ofAddress(a); }
    private static MethodHandle handle(long addr, FunctionDescriptor desc) {
        if (addr == 0L) throw new IllegalStateException("null GL fn ptr");
        return LINKER.downcallHandle(fn(addr), desc);
    }

    // ── Session state ──────────────────────────────────────────────────────────
    private static volatile boolean initDone   = false;
    private static volatile boolean initFailed = false;

    // Shared GLES objects (created once in ensureInit)
    private static int glVao, glVbo, glProgram, glTexUniform, glPositionAttr;
    private static int glProgram2D, glTexUniform2D; // GL_TEXTURE_2D path
    /** -1 = not yet determined, 0 = EXTERNAL_OES works, 1 = GL_TEXTURE_2D works */
    private static volatile int preferredTarget = -1;

    // Per-frame method handles
    private static MethodHandle mhGenTextures, mhBindTexture, mhDelTextures, mhTexParamI;
    private static MethodHandle mhEGLImageTarget;
    private static MethodHandle mhGenFBOs, mhBindFBO, mhDelFBOs;
    private static MethodHandle mhGenRBO, mhBindRBO, mhDelRBO, mhRBOStorage;
    private static MethodHandle mhFBORenderbuffer, mhCheckFBO;
    private static MethodHandle mhViewport, mhReadPixels;
    private static MethodHandle mhClearColor, mhClear, mhGetError;
    private static MethodHandle mhUseProgram, mhBindBuffer, mhEnableVAA;
    private static MethodHandle mhVertexAttribPtr, mhDrawArrays, mhUniform1i;

    private static final java.util.concurrent.atomic.AtomicInteger DIAG_COUNTER
            = new java.util.concurrent.atomic.AtomicInteger();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("waylandcraft/vulkanmod/EglImageReader");

    // ── Per-frame readback ────────────────────────────────────────────────────

    /**
     * Import eglImage as EXTERNAL_OES, draw it onto a renderbuffer FBO,
     * then glReadPixels.  No blit (EXTERNAL_OES cannot be FBO source).
     */
    private static long doReadPixels(long eglImage, int w, int h) {
        long buf = MemoryUtil.nmemAlloc((long) w * h * 4);
        if (buf == 0L) return 0L;

        try (MemoryStack stack = stackPush()) {
            IntBuffer idbuf = stack.mallocInt(1);
            long ibaddr = MemoryUtil.memAddress(idbuf);

            // ── Import EGLImage ────────────────────────────────────────────────
            // Try GL_TEXTURE_2D first (Intel/Mesa: supports GL_OES_EGL_image,
            // but not GL_OES_EGL_image_external in a non-display GLES context).
            // Fall back to GL_TEXTURE_EXTERNAL_OES if that fails (NVIDIA).
            // After the first successful import we cache the result in
            // `preferredTarget` to avoid the retry overhead on every frame.
            mhGenTextures.invokeExact(1, ptr(ibaddr));
            int extTex = idbuf.get(0);
            boolean use2D = false;
            if (preferredTarget == -1 || preferredTarget == 1) {
                // Try GL_TEXTURE_2D
                mhBindTexture.invokeExact(GL_TEXTURE_2D, extTex);
                mhTexParamI.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                mhTexParamI.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                mhTexParamI.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                mhTexParamI.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                mhEGLImageTarget.invokeExact(GL_TEXTURE_2D, ptr(eglImage));
                int err2D = (int) mhGetError.invokeExact();
                if (err2D == 0) {
                    use2D = true;
                    preferredTarget = 1;
                    LOG.debug("[waylandcraft/vulkanmod/EglImageReader] using GL_TEXTURE_2D path");
                } else {
                    LOG.info("[waylandcraft/vulkanmod/EglImageReader] GL_TEXTURE_2D import failed 0x{}, "
                            + "trying EXTERNAL_OES", Integer.toHexString(err2D));
                    mhBindTexture.invokeExact(GL_TEXTURE_2D, 0);
                }
            }
            if (!use2D) {
                mhBindTexture.invokeExact(GL_TEXTURE_EXTERNAL_OES, extTex);
                mhTexParamI.invokeExact(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                mhTexParamI.invokeExact(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                mhTexParamI.invokeExact(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                mhTexParamI.invokeExact(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                mhEGLImageTarget.invokeExact(GL_TEXTURE_EXTERNAL_OES, ptr(eglImage));
                int errExt = (int) mhGetError.invokeExact();
                if (errExt != 0) {
                    LOG.warn("[waylandcraft/vulkanmod/EglImageReader] both GL_TEXTURE_2D and "
                            + "EXTERNAL_OES failed (0x{}) — EGL import unavailable on this driver",
                            Integer.toHexString(errExt));
                    mhBindTexture.invokeExact(GL_TEXTURE_EXTERNAL_OES, 0);
                    mhDelTextures.invokeExact(1, ptr(ibaddr));
                    MemoryUtil.nmemFree(buf);
                    return 0L;
                }
                preferredTarget = 0;
                LOG.debug("[waylandcraft/vulkanmod/EglImageReader] using EXTERNAL_OES path");
            }

            // ── Renderbuffer FBO ───────────────────────────────────────────────
            mhGenRBO.invokeExact(1, ptr(ibaddr));
            int rbo = idbuf.get(0);
            mhBindRBO.invokeExact(GL_RENDERBUFFER, rbo);
            mhRBOStorage.invokeExact(GL_RENDERBUFFER, GL_RGBA8, w, h);

            mhGenFBOs.invokeExact(1, ptr(ibaddr));
            int fbo = idbuf.get(0);
            mhBindFBO.invokeExact(GL_FRAMEBUFFER, fbo);
            mhFBORenderbuffer.invokeExact(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                           GL_RENDERBUFFER, rbo);

            int status = (int) mhCheckFBO.invokeExact(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                LOG.error("[waylandcraft/vulkanmod/EglImageReader] FBO incomplete 0x{}",
                          Integer.toHexString(status));
                cleanupDraw(extTex, rbo, fbo, ibaddr);
                MemoryUtil.nmemFree(buf);
                return 0L;
            }

            // ── Draw EXTERNAL_OES quad ─────────────────────────────────────────
            // Vertex shader uses gl_VertexID — no vertex attributes needed.
            // The empty bound VAO satisfies GLES3 core without any attrib state.
            mhViewport.invokeExact(0, 0, w, h);
            // Clear the FBO before drawing so a failed EGLImage import yields
            // transparent zeros rather than undefined GPU memory contents.
            mhClearColor.invokeExact(0.0f, 0.0f, 0.0f, 0.0f);
            mhClear.invokeExact(0x4000); // GL_COLOR_BUFFER_BIT
            if (use2D && glProgram2D != 0) {
                mhUseProgram.invokeExact(glProgram2D);
                mhBindTexture.invokeExact(GL_TEXTURE_2D, extTex);
                mhUniform1i.invokeExact(glTexUniform2D, 0);
            } else {
                mhUseProgram.invokeExact(glProgram);
                mhBindTexture.invokeExact(GL_TEXTURE_EXTERNAL_OES, extTex);
                mhUniform1i.invokeExact(glTexUniform, 0);
            }
            mhDrawArrays.invokeExact(GL_TRIANGLE_STRIP, 0, 4);

            // ── Diagnostic: check draw succeeded and sample centre pixel ───────
            if (DIAG_COUNTER.getAndIncrement() < 5) {
                int drawErr = (int) mhGetError.invokeExact();
                // Temporarily read a 1×1 pixel at the centre to verify content.
                // We read into a tiny stack buffer so we don't clobber buf.
                long onePx = MemoryUtil.nmemAlloc(4);
                if (onePx != 0L) {
                    mhReadPixels.invokeExact(w / 2, h / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, ptr(onePx));
                    int r = Byte.toUnsignedInt(MemoryUtil.memGetByte(onePx));
                    int g = Byte.toUnsignedInt(MemoryUtil.memGetByte(onePx + 1));
                    int b = Byte.toUnsignedInt(MemoryUtil.memGetByte(onePx + 2));
                    int a = Byte.toUnsignedInt(MemoryUtil.memGetByte(onePx + 3));
                    MemoryUtil.nmemFree(onePx);
                    LOG.info("[waylandcraft/vulkanmod/EglImageReader] diag frame={} drawErr=0x{} centre=rgba({},{},{},{})",
                            DIAG_COUNTER.get() - 1,
                            Integer.toHexString(drawErr), r, g, b, a);
                }
            }

            // ── Read back ──────────────────────────────────────────────────────
            mhReadPixels.invokeExact(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, ptr(buf));

            cleanupDraw(extTex, rbo, fbo, ibaddr);

        } catch (Throwable t) {
            LOG.error("[waylandcraft/vulkanmod/EglImageReader] doReadPixels failed", t);
            MemoryUtil.nmemFree(buf);
            return 0L;
        }
        return buf;
    }

    private static void cleanupDraw(int extTex, int rbo, int fbo, long ibaddr)
            throws Throwable {
        mhBindFBO.invokeExact(GL_FRAMEBUFFER, 0);
        // Unbind from whichever target was used
        if (preferredTarget == 1) mhBindTexture.invokeExact(GL_TEXTURE_2D, 0);
        else mhBindTexture.invokeExact(GL_TEXTURE_EXTERNAL_OES, 0);
        MemoryUtil.memPutInt(ibaddr, fbo);
        mhDelFBOs.invokeExact(1, ptr(ibaddr));
        mhBindRBO.invokeExact(GL_RENDERBUFFER, 0);
        MemoryUtil.memPutInt(ibaddr, rbo);
        mhDelRBO.invokeExact(1, ptr(ibaddr));
        mhBindTexture.invokeExact(GL_TEXTURE_EXTERNAL_OES, 0);
        MemoryUtil.memPutInt(ibaddr, extTex);
        mhDelTextures.invokeExact(1, ptr(ibaddr));
    }

    // ── One-time init ─────────────────────────────────────────────────────────

    private static final String VERT_SRC =
        "#version 300 es\n" +
        // Use gl_VertexID so the shader needs ZERO vertex attributes.
        // An empty VAO (no attribs enabled) satisfies GLES3 core, and the
        // vertex-data NULL-deref in glDrawArrays is completely bypassed.
        "out vec2 vTexCoord;\n" +
        "void main() {\n" +
        "  vec2 p[4];" +
        "  p[0] = vec2(-1.0,-1.0);" +
        "  p[1] = vec2( 1.0,-1.0);" +
        "  p[2] = vec2(-1.0, 1.0);" +
        "  p[3] = vec2( 1.0, 1.0);" +
        "  vTexCoord = p[gl_VertexID] * 0.5 + 0.5;\n" +
        "  gl_Position = vec4(p[gl_VertexID], 0.0, 1.0);\n" +
        "}";

    /** EXTERNAL_OES path — NVIDIA, drivers with GL_OES_EGL_image_external */
    private static final String FRAG_SRC =
        "#version 300 es\n" +
        "#extension GL_OES_EGL_image_external_essl3 : require\n" +
        "precision mediump float;\n" +
        "uniform samplerExternalOES uTex;\n" +
        "in vec2 vTexCoord;\n" +
        "out vec4 fragColor;\n" +
        "void main() {\n" +
        "  fragColor = texture(uTex, vTexCoord);\n" +
        "}";

    /** GL_TEXTURE_2D path — Intel/Mesa, drivers without EXTERNAL_OES */
    private static final String FRAG_SRC_2D =
        "#version 300 es\n" +
        "precision mediump float;\n" +
        "uniform sampler2D uTex;\n" +
        "in vec2 vTexCoord;\n" +
        "out vec4 fragColor;\n" +
        "void main() {\n" +
        "  fragColor = texture(uTex, vTexCoord);\n" +
        "}";

    private static synchronized boolean ensureInit(long display) {
        if (initDone) return !initFailed;
        initDone = true;

        LOG.info("[waylandcraft/vulkanmod/EglImageReader] Initialising GLES3 context on thread '{}'",
                 Thread.currentThread().getName());

        try {
            // ── EGL context ─────────────────────────────────────────────────────
            long fnBindAPI      = EglHelper.getProc("eglBindAPI");
            long fnChooseCfg    = EglHelper.getProc("eglChooseConfig");
            long fnCreateCtx    = EglHelper.getProc("eglCreateContext");
            long fnCreatePbuf   = EglHelper.getProc("eglCreatePbufferSurface");
            long fnMakeCurrent  = EglHelper.getProc("eglMakeCurrent");

            if (fnChooseCfg == 0 || fnCreateCtx == 0 || fnCreatePbuf == 0 ||
                fnMakeCurrent == 0) {
                LOG.error("[waylandcraft/vulkanmod/EglImageReader] EGL functions missing");
                return fail();
            }

            try (MemoryStack stack = stackPush()) {
                if (fnBindAPI != 0)
                    JNI.invokePI((long) EGL_OPENGL_ES_API, fnBindAPI);

                long cfgAttr = MemoryUtil.memAddress(stack.ints(
                    EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE));
                LongBuffer cfgBuf = stack.mallocLong(1);
                IntBuffer  nCfg   = stack.mallocInt(1);
                int ok = JNI.invokePPPPPI(display, cfgAttr,
                                          MemoryUtil.memAddress(cfgBuf), 1L,
                                          MemoryUtil.memAddress(nCfg), fnChooseCfg);
                if (ok == 0 || nCfg.get(0) == 0) {
                    LOG.error("[waylandcraft/vulkanmod/EglImageReader] eglChooseConfig: no GLES3 config");
                    return fail();
                }
                long cfg = cfgBuf.get(0);
                long ctxAttr = MemoryUtil.memAddress(stack.ints(
                    EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE));
                long ctx = JNI.invokePPPPP(display, cfg, 0L, ctxAttr, fnCreateCtx);
                if (ctx == 0L) {
                    LOG.error("[waylandcraft/vulkanmod/EglImageReader] eglCreateContext failed");
                    return fail();
                }
                long pbAttr = MemoryUtil.memAddress(stack.ints(
                    EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE));
                long pbuf = JNI.invokePPPP(display, cfg, pbAttr, fnCreatePbuf);
                if (pbuf == 0L) {
                    LOG.error("[waylandcraft/vulkanmod/EglImageReader] eglCreatePbufferSurface failed");
                    return fail();
                }
                int mc = JNI.invokePPPPI(display, pbuf, pbuf, ctx, fnMakeCurrent);
                if (mc == 0) {
                    LOG.error("[waylandcraft/vulkanmod/EglImageReader] eglMakeCurrent failed");
                    return fail();
                }
            }
            LOG.info("[waylandcraft/vulkanmod/EglImageReader] EGL context current on worker thread");

            // ── Resolve GLES functions ──────────────────────────────────────────
            long fnGenTex     = gl("glGenTextures");
            long fnBindTex    = gl("glBindTexture");
            long fnDelTex     = gl("glDeleteTextures");
            long fnTexParam   = gl("glTexParameteri");
            long fnEGLTarget  = gl("glEGLImageTargetTexture2DOES");
            long fnGenFBOs    = gl("glGenFramebuffers");
            long fnBindFBO    = gl("glBindFramebuffer");
            long fnDelFBOs    = gl("glDeleteFramebuffers");
            long fnGenRBO     = gl("glGenRenderbuffers");
            long fnBindRBO    = gl("glBindRenderbuffer");
            long fnDelRBO     = gl("glDeleteRenderbuffers");
            long fnRBOStorage = gl("glRenderbufferStorage");
            long fnFBORBO     = gl("glFramebufferRenderbuffer");
            long fnCheckFBO   = gl("glCheckFramebufferStatus");
            long fnViewport   = gl("glViewport");
            long fnReadPix    = gl("glReadPixels");
            long fnGenBufs    = gl("glGenBuffers");
            long fnBindBuf    = gl("glBindBuffer");
            long fnBufData    = gl("glBufferData");
            long fnUseProg    = gl("glUseProgram");
            long fnEnVAA      = gl("glEnableVertexAttribArray");
            long fnVtxPtr     = gl("glVertexAttribPointer");
            long fnDraw       = gl("glDrawArrays");
            long fnUniform    = gl("glUniform1i");
            long fnClearColor = gl("glClearColor");
            long fnClear      = gl("glClear");
            long fnGetError   = gl("glGetError");
            long fnGenVAO     = gl("glGenVertexArrays");
            long fnBindVAO    = gl("glBindVertexArray");
            long fnCreateSh   = gl("glCreateShader");
            long fnShSrc      = gl("glShaderSource");
            long fnCompSh     = gl("glCompileShader");
            long fnGetShiv    = gl("glGetShaderiv");
            long fnCreateProg = gl("glCreateProgram");
            long fnAttachSh   = gl("glAttachShader");
            long fnLinkProg   = gl("glLinkProgram");
            long fnGetProgiv  = gl("glGetProgramiv");
            long fnGetAttrib  = gl("glGetAttribLocation");
            long fnGetUniform = gl("glGetUniformLocation");

            for (long ptr : new long[]{fnGenTex, fnBindTex, fnEGLTarget,
                    fnGenFBOs, fnBindFBO, fnReadPix, fnGenBufs, fnBindBuf,
                    fnBufData, fnUseProg, fnDraw, fnGenVAO, fnBindVAO,
                    fnCreateSh, fnLinkProg}) {
                if (ptr == 0L) {
                    LOG.error("[waylandcraft/vulkanmod/EglImageReader] Required GLES fn missing");
                    return fail();
                }
            }

            // ── Build method handles ────────────────────────────────────────────
            var ipV = FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS);
            mhGenTextures = handle(fnGenTex,   ipV);
            mhDelTextures = handle(fnDelTex,   ipV);
            mhGenFBOs     = handle(fnGenFBOs,  ipV);
            mhDelFBOs     = handle(fnDelFBOs,  ipV);
            mhGenRBO      = handle(fnGenRBO,   ipV);
            mhDelRBO      = handle(fnDelRBO,   ipV);
            mhGenBufs     = handle(fnGenBufs,  ipV);

            var iiV = FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT);
            mhBindTexture = handle(fnBindTex,  iiV);
            mhBindFBO     = handle(fnBindFBO,  iiV);
            mhBindRBO     = handle(fnBindRBO,  iiV);
            mhBindBuffer  = handle(fnBindBuf,  iiV);
            mhUniform1i   = handle(fnUniform,  iiV);
            mhUseProgram  = handle(fnUseProg,
                FunctionDescriptor.ofVoid(JAVA_INT));
            mhEnableVAA   = handle(fnEnVAA,
                FunctionDescriptor.ofVoid(JAVA_INT));

            mhTexParamI = handle(fnTexParam,
                FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT));
            mhEGLImageTarget = handle(fnEGLTarget,
                FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
            mhRBOStorage = handle(fnRBOStorage,
                FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
            mhFBORenderbuffer = handle(fnFBORBO,
                FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
            mhCheckFBO = handle(fnCheckFBO,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT));
            mhViewport = handle(fnViewport,
                FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
            mhReadPixels = handle(fnReadPix,
                FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                                          JAVA_INT, JAVA_INT, ADDRESS));
            mhVertexAttribPtr = handle(fnVtxPtr,
                FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT,
                                          JAVA_INT, JAVA_INT, ADDRESS));
            mhDrawArrays = handle(fnDraw,
                FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT));

            mhClearColor = handle(fnClearColor,
                FunctionDescriptor.ofVoid(JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));
            mhClear      = handle(fnClear,
                FunctionDescriptor.ofVoid(JAVA_INT));
            mhGetError   = handle(fnGetError,
                FunctionDescriptor.of(JAVA_INT));

            // ── VAO — MUST come before VBO/attrib setup (GLES3 core requirement) ─
            // GLES2 had an implicit default VAO; GLES3 core does not.
            // Without a bound VAO, glEnableVertexAttribArray / glVertexAttribPointer
            // write to a NULL driver struct → crash in glDrawArrays.
            try (MemoryStack s = stackPush()) {
                IntBuffer vaoBuf = s.mallocInt(1);
                handle(fnGenVAO, FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS))
                    .invokeExact(1, ptr(MemoryUtil.memAddress(vaoBuf)));
                glVao = vaoBuf.get(0);
                handle(fnBindVAO, FunctionDescriptor.ofVoid(JAVA_INT))
                    .invokeExact(glVao);
                LOG.info("[waylandcraft/vulkanmod/EglImageReader] VAO {} bound", glVao);
            }

            // ── VBO — quad for full-screen draw ────────────────────────────────
            try (MemoryStack s = stackPush()) {
                IntBuffer vboBuf = s.mallocInt(1);
                mhGenBufs.invokeExact(1, ptr(MemoryUtil.memAddress(vboBuf)));
                glVbo = vboBuf.get(0);
                mhBindBuffer.invokeExact(GL_ARRAY_BUFFER, glVbo);

                java.nio.ByteBuffer vdata = s.malloc(4 * 2 * 4);
                vdata.putFloat(-1f).putFloat(-1f);
                vdata.putFloat( 1f).putFloat(-1f);
                vdata.putFloat(-1f).putFloat( 1f);
                vdata.putFloat( 1f).putFloat( 1f);
                vdata.flip();
                handle(fnBufData, FunctionDescriptor.ofVoid(
                        JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT))
                    .invokeExact(GL_ARRAY_BUFFER, (long) vdata.remaining(),
                                 ptr(MemoryUtil.memAddress(vdata)), GL_STATIC_DRAW);
                LOG.info("[waylandcraft/vulkanmod/EglImageReader] VBO {} uploaded", glVbo);
            }

            // ── Shader program ──────────────────────────────────────────────────
            glProgram = buildProgram(fnCreateSh, fnShSrc, fnCompSh, fnGetShiv,
                                     fnCreateProg, fnAttachSh, fnLinkProg, fnGetProgiv);
            if (glProgram == 0) return fail();

            // Also compile the GL_TEXTURE_2D variant (Intel/Mesa path).
            // Uses sampler2D — no extension required.
            glProgram2D = buildProgram2D(fnCreateSh, fnShSrc, fnCompSh, fnGetShiv,
                                         fnCreateProg, fnAttachSh, fnLinkProg, fnGetProgiv);
            // glProgram2D == 0 is non-fatal; we will fall through to the EXT path.

            // ── Attribute / uniform locations ───────────────────────────────────
            glPositionAttr = 0; // unused (vertex shader uses gl_VertexID)
            glTexUniform   = namedInt(fnGetUniform, "uTex");
            if (glProgram2D != 0) {
                glTexUniform2D = namedInt2D(fnGetUniform, "uTex");
            }
            LOG.info("[waylandcraft/vulkanmod/EglImageReader] aPos={} uTex={} uTex2D={}",
                     glPositionAttr, glTexUniform, glTexUniform2D);

            LOG.info("[waylandcraft/vulkanmod/EglImageReader] GLES3 readback context ready " +
                     "(thread '{}', EXTERNAL_OES + draw call + VAO {})",
                     Thread.currentThread().getName(), glVao);
            return true;

        } catch (Throwable t) {
            LOG.error("[waylandcraft/vulkanmod/EglImageReader] Init failed", t);
            return fail();
        }
    }

    // ── Method handles needed only during init ─────────────────────────────────
    private static MethodHandle mhGenBufs;

    private static int buildProgram(long fnCreate, long fnSrc, long fnCompile,
                                     long fnGetShiv, long fnCreateProg,
                                     long fnAttach, long fnLink, long fnGetPiv)
            throws Throwable {
        var iV  = FunctionDescriptor.of(JAVA_INT);
        var iiV = FunctionDescriptor.of(JAVA_INT, JAVA_INT);
        var shSrcDesc = FunctionDescriptor.ofVoid(
            JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
        var iiiV = FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS);

        int vs = (int) handle(fnCreate, iiV).invokeExact(GL_VERTEX_SHADER);
        int fs = (int) handle(fnCreate, iiV).invokeExact(GL_FRAGMENT_SHADER);

        for (int[] pair : new int[][]{{vs, 0}, {fs, 1}}) {
            String src = pair[1] == 0 ? VERT_SRC : FRAG_SRC;
            try (MemoryStack s = stackPush()) {
                long strPtr = MemoryUtil.memAddress(
                    MemoryUtil.memUTF8(src, true));
                long ppStr  = MemoryUtil.memAddress(s.mallocLong(1));
                MemoryUtil.memPutLong(ppStr, strPtr);
                handle(fnSrc, shSrcDesc).invokeExact(pair[0], 1, ptr(ppStr), ptr(0L));
                handle(fnCompile, FunctionDescriptor.ofVoid(JAVA_INT)).invokeExact(pair[0]);
                IntBuffer stat = s.mallocInt(1);
                handle(fnGetShiv, iiiV).invokeExact(pair[0], GL_COMPILE_STATUS,
                                                     ptr(MemoryUtil.memAddress(stat)));
                if (stat.get(0) != GL_TRUE) {
                    LOG.error("[waylandcraft/vulkanmod/EglImageReader] Shader {} compile failed",
                              pair[1] == 0 ? "vert" : "frag");
                    return 0;
                }
            }
        }
        LOG.info("[waylandcraft/vulkanmod/EglImageReader] Shaders compiled");

        int prog = (int) handle(fnCreateProg, iV).invokeExact();
        handle(fnAttach, FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT))
            .invokeExact(prog, vs);
        handle(fnAttach, FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT))
            .invokeExact(prog, fs);
        handle(fnLink,  FunctionDescriptor.ofVoid(JAVA_INT)).invokeExact(prog);

        try (MemoryStack s = stackPush()) {
            IntBuffer stat = s.mallocInt(1);
            handle(fnGetPiv, iiiV).invokeExact(prog, GL_LINK_STATUS,
                                               ptr(MemoryUtil.memAddress(stat)));
            if (stat.get(0) != GL_TRUE) {
                LOG.error("[waylandcraft/vulkanmod/EglImageReader] Program link failed");
                return 0;
            }
        }
        LOG.info("[waylandcraft/vulkanmod/EglImageReader] Program {} linked", prog);
        return prog;
    }

    private static int namedInt(long fn, String name) throws Throwable {
        try (MemoryStack s = stackPush()) {
            long strPtr = MemoryUtil.memAddress(MemoryUtil.memUTF8(name, true));
            return (int) handle(fn,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS))
                .invokeExact(glProgram, ptr(strPtr));
        }
    }

    /** Same as buildProgram() but uses FRAG_SRC_2D (sampler2D, no extension). */
    private static int buildProgram2D(long fnCreate, long fnSrc, long fnCompile,
                                      long fnGetShiv, long fnCreateProg,
                                      long fnAttach, long fnLink, long fnGetPiv)
            throws Throwable {
        var iV  = FunctionDescriptor.of(JAVA_INT);
        var iiV = FunctionDescriptor.of(JAVA_INT, JAVA_INT);
        var shSrcDesc = FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
        var iiiV = FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS);

        int vs = (int) handle(fnCreate, iiV).invokeExact(GL_VERTEX_SHADER);
        int fs = (int) handle(fnCreate, iiV).invokeExact(GL_FRAGMENT_SHADER);

        for (int[] pair : new int[][]{{vs, 0}, {fs, 1}}) {
            String shSrc = pair[1] == 0 ? VERT_SRC : FRAG_SRC_2D;
            try (MemoryStack s = stackPush()) {
                long strPtr = MemoryUtil.memAddress(MemoryUtil.memUTF8(shSrc, true));
                long ppStr  = MemoryUtil.memAddress(s.mallocLong(1));
                MemoryUtil.memPutLong(ppStr, strPtr);
                handle(fnSrc, shSrcDesc).invokeExact(pair[0], 1, ptr(ppStr), ptr(0L));
                handle(fnCompile, FunctionDescriptor.ofVoid(JAVA_INT)).invokeExact(pair[0]);
                IntBuffer stat = s.mallocInt(1);
                handle(fnGetShiv, iiiV).invokeExact(pair[0], GL_COMPILE_STATUS,
                                                     ptr(MemoryUtil.memAddress(stat)));
                if (stat.get(0) != GL_TRUE) {
                    LOG.warn("[waylandcraft/vulkanmod/EglImageReader] 2D shader {} compile failed (non-fatal)",
                              pair[1] == 0 ? "vert" : "frag");
                    return 0;
                }
            }
        }
        int prog = (int) handle(fnCreateProg, iV).invokeExact();
        handle(fnAttach, FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT)).invokeExact(prog, vs);
        handle(fnAttach, FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT)).invokeExact(prog, fs);
        handle(fnLink,   FunctionDescriptor.ofVoid(JAVA_INT)).invokeExact(prog);
        try (MemoryStack s = stackPush()) {
            IntBuffer stat = s.mallocInt(1);
            handle(fnGetPiv, iiiV).invokeExact(prog, GL_LINK_STATUS,
                                               ptr(MemoryUtil.memAddress(stat)));
            if (stat.get(0) != GL_TRUE) {
                LOG.warn("[waylandcraft/vulkanmod/EglImageReader] 2D program link failed (non-fatal)");
                return 0;
            }
        }
        LOG.info("[waylandcraft/vulkanmod/EglImageReader] 2D program {} linked", prog);
        return prog;
    }

    private static int namedInt2D(long fn, String name) throws Throwable {
        try (MemoryStack s = stackPush()) {
            long strPtr = MemoryUtil.memAddress(MemoryUtil.memUTF8(name, true));
            return (int) handle(fn,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS))
                .invokeExact(glProgram2D, ptr(strPtr));
        }
    }

    private static long gl(String n) { return EglHelper.getProc(n); }
    private static boolean fail()    { initFailed = true; return false; }
}
