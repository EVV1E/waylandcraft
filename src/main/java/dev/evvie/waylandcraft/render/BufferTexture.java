package dev.evvie.waylandcraft.render;

import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf;
import dev.evvie.waylandcraft.egl.EGL;
import dev.evvie.waylandcraft.egl.EGLHelper;
import net.minecraft.client.Minecraft;

// 1.21.1 renders through OpenGL only, so buffers are plain GL textures
// identified by their texture id (-1 when there is none).
public abstract class BufferTexture {
	
	public static final int FORMAT_ARGB8888 = 0;
	public static final int FORMAT_XRGB8888 = 1;
	
	public final int width;
	public final int height;
	public final int format;
	
	public BufferTexture(int width, int height, int format) {
		this.width = width;
		this.height = height;
		this.format = format;
	}
	
	public abstract int getTextureId();
	public abstract void release();
	
	public static BufferTexture createShmTexture(long ptr, int width, int height, int format, int stride) {
		return new ShmBufferTexture(ptr, width, height, format, stride);
	}
	
	public static BufferTexture createSinglePixelTexture(byte r, byte g, byte b, byte a) {
		return new SinglePixelBufferTexture(r, g, b, a);
	}
	
	public static DmabufTexture createDmabufTexture(Dmabuf dmabuf) throws DmabufImportFailedException {
		return new GlDmabufTexture(dmabuf);
	}
	
	private static int createTexture() {
		int id = GlStateManager._genTexture();
		GlStateManager._bindTexture(id);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LEVEL, 0);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_LOD, 0);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LOD, 0);
		
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_NEAREST);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
		GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);
		return id;
	}
	
	private static class SinglePixelBufferTexture extends BufferTexture {
		
		private int id;
		
		private SinglePixelBufferTexture(byte r, byte g, byte b, byte a) {
			super(1, 1, FORMAT_ARGB8888);
			
			id = createTexture();
			try(MemoryStack stack = MemoryStack.stackPush()) {
				ByteBuffer pixel = stack.bytes(r, g, b, a);
				GlStateManager._pixelStore(GL33.GL_UNPACK_ROW_LENGTH, 0);
				GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_PIXELS, 0);
				GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_ROWS, 0);
				GlStateManager._pixelStore(GL33.GL_UNPACK_ALIGNMENT, 4);
				GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, 1, 1, 0, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, pixel);
			}
		}
		
		@Override
		public int getTextureId() {
			return id;
		}
		
		@Override
		public void release() {
			if(id < 0) return;
			GlStateManager._deleteTexture(id);
			id = -1;
		}
		
	}
	
	private static class ShmBufferTexture extends BufferTexture {
		
		private int id;
		
		private ShmBufferTexture(long ptr, int width, int height, int format, int stride) {
			super(width, height, format);
			
			id = createTexture();
			GlStateManager._pixelStore(GL33.GL_UNPACK_ROW_LENGTH, stride / 4);
			GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_PIXELS, 0);
			GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_ROWS, 0);
			GlStateManager._pixelStore(GL33.GL_UNPACK_ALIGNMENT, 4);
			
			GL33.nglTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, width, height, 0, GL33.GL_BGRA, GL33.GL_UNSIGNED_INT_8_8_8_8_REV, ptr);
			GlStateManager._pixelStore(GL33.GL_UNPACK_ROW_LENGTH, 0);
		}
		
		@Override
		public int getTextureId() {
			return id;
		}
		
		@Override
		public void release() {
			if(id < 0) return;
			GlStateManager._deleteTexture(id);
			id = -1;
		}
		
	}
	
	public static abstract class DmabufTexture extends BufferTexture {
		
		public final long handle;
		protected RenderTarget target;
		protected int internalTexture = -1;
		
		private DmabufTexture(Dmabuf buf) throws DmabufImportFailedException {
			super(buf.width(), buf.height(), BufferTexture.FORMAT_ARGB8888);
			this.handle = buf.handle();
			
			target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
		}
		
		// Destroys internal data
		public abstract void doFree();
		
		@Override
		public int getTextureId() {
			if(target == null) return -1;
			return target.getColorTextureId();
		}
		
		public void copyData() {
			if(internalTexture < 0 || target == null) return;
			
			WindowShaders.beginTarget(target, true);
			WindowShaders.drawFullscreen(WindowShaders.window, internalTexture, true);
			WindowShaders.endTargets();
		}
		
		public void doReleaseTexure() {
			target.destroyBuffers();
			target = null;
		}
		
		@Override
		public void release() {
			// Don't release texture id as dmabuf textures might get reused
		}
		
	}
	
	public static class DmabufImportFailedException extends Exception {
	}
	
	private static class GlDmabufTexture extends DmabufTexture {
		
		private final long eglImage;
		
		private GlDmabufTexture(Dmabuf buf) throws DmabufImportFailedException {
			super(buf);
			
			long dpy = EGL.getEGLDisplay();
			eglImage = EGLHelper.importDmabufToImage(dpy, buf);
			if(eglImage == EGL.EGL_NO_IMAGE) {
				WaylandCraftCommon.LOGGER.error("Failed to import dmabuf! EGL error: " + EGL.eglGetErrorString());
				target.destroyBuffers();
				throw new DmabufImportFailedException();
			}
			
			init();
			copyData();
		}
		
		private void init() {
			/* Create texture for EGLImage */
			internalTexture = createTexture();
			
			long glEGLImageTargetTexture2DOES = GLFW.glfwGetProcAddress("glEGLImageTargetTexture2DOES");
			JNI.invokeJV(GL33.GL_TEXTURE_2D, eglImage, glEGLImageTargetTexture2DOES);
		}
		
		@Override
		public void doFree() {
			if(internalTexture < 0) return;
			
			long dpy = EGL.getEGLDisplay();
			EGL.eglDestroyImage(dpy, eglImage);
			
			GlStateManager._deleteTexture(internalTexture);
			internalTexture = -1;
		}
		
	}
	
}
