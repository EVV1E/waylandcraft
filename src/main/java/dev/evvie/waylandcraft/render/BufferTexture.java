package dev.evvie.waylandcraft.render;

import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeEGL;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.JNI;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.mixin.IGlTextureMixin;

public abstract class BufferTexture {
	
	public static final int FORMAT_ARGB8888 = 0;
	public static final int FORMAT_XRGB8888 = 1;
	
	public final int id;
	public final int width;
	public final int height;
	public final int format;
	public GpuTextureView textureView;
	
	public BufferTexture(int width, int height, int format) {
		this.width = width;
		this.height = height;
		this.format = format;
		this.id = GlStateManager._genTexture();
		GlTexture glTexture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "buffertexture-" + this.hashCode(), TextureFormat.RGBA8, width, height, 1, 1, id);
		this.textureView = RenderSystem.getDevice().createTextureView(glTexture);
	}
	
	public void release() {
		GlStateManager._deleteTexture(id);
		textureView = null;
	}
	
	public static class ShmBufferTexture extends BufferTexture {
		
		private final long ptr;
		private final int stride;
		
		public ShmBufferTexture(long ptr, int width, int height, int format, int stride) {
			super(width, height, format);
			this.ptr = ptr;
			this.stride = stride;
//			if(stride % 4 != 0) WaylandCraft.LOGGER.info("Stride is not a multiple of 4 bytes!!");
			
			init();
		}
		
		private void init() {
			GlStateManager._bindTexture(this.id);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LEVEL, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_LOD, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LOD, 0);
			
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
			
			GlStateManager._pixelStore(GL33.GL_UNPACK_ROW_LENGTH, stride / 4);
			GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_PIXELS, 0);
			GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_ROWS, 0);
			GlStateManager._pixelStore(GL33.GL_UNPACK_ALIGNMENT, 4);
			
			GL33.nglTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, width, height, 0, GL33.GL_BGRA, GL33.GL_UNSIGNED_INT_8_8_8_8_REV, this.ptr);
		}
		
	}
	
	public static class SinglePixelBufferTexture extends BufferTexture {
		
		public final byte r;
		public final byte g;
		public final byte b;
		public final byte a;
		
		public SinglePixelBufferTexture(byte r, byte g, byte b, byte a) {
			super(1, 1, BufferTexture.FORMAT_ARGB8888);
			this.r = r;
			this.g = g;
			this.b = b;
			this.a = a;
			
			init();
		}
		
		private void init() {
			GlStateManager._bindTexture(this.id);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LEVEL, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_LOD, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LOD, 0);
			
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_NEAREST);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
			
			GlStateManager._pixelStore(GL33.GL_UNPACK_ROW_LENGTH, 0);
			GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_PIXELS, 0);
			GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_ROWS, 0);
			GlStateManager._pixelStore(GL33.GL_UNPACK_ALIGNMENT, 4);
			
			ByteBuffer buf = ByteBuffer.allocateDirect(4);
			buf.put(b);
			buf.put(g);
			buf.put(r);
			buf.put(a);
			buf.rewind();
			GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, width, height, 0, GL33.GL_BGRA, GL33.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
		}
		
	}
	
	public static class DmabufTexture extends BufferTexture {
		
		public final long handle;
		private final long eglImage;
		
		public DmabufTexture(long handle, long eglImage, int width, int height) {
			super(width, height, BufferTexture.FORMAT_ARGB8888);
			this.handle = handle;
			this.eglImage = eglImage;
			init();
		}
		
		private void init() {
			GlStateManager._bindTexture(this.id);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LEVEL, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_LOD, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LOD, 0);
			
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
			
			long glEGLImageTargetTexture2DOES = GLFW.glfwGetProcAddress("glEGLImageTargetTexture2DOES");
			JNI.invokeJV(GL33.GL_TEXTURE_2D, this.eglImage, glEGLImageTargetTexture2DOES);
		}
		
		@Override
		public void release() {
			// Don't release texture id as dmabuf textures might get reused
		}
		
		public void free() {
			super.release();
			
			long eglDestroyImage = GLFW.glfwGetProcAddress("eglDestroyImage");
			long display = GLFWNativeEGL.glfwGetEGLDisplay();
			
			JNI.invokePPI(display, this.eglImage, eglDestroyImage);
		}
		
	}
	
}
