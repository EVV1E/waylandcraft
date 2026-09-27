package dev.evvie.waylandcraft.render;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf;
import dev.evvie.waylandcraft.egl.EGL;
import dev.evvie.waylandcraft.egl.EGLHelper;
import dev.evvie.waylandcraft.mixin.IGlTextureMixin;
import net.minecraft.client.renderer.RenderPipelines;

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
	
	public abstract GpuTextureView getTextureView();
	public abstract void release();
	
	public static BufferTexture createShmTexture(long ptr, int width, int height, int format, int stride) {
		GpuDeviceBackend deviceBackend = RenderSystem.getDevice().backend;
		if(deviceBackend instanceof GlDevice) {
			return new GlShmBufferTexture(ptr, width, height, format, stride);
		}
		
		throw new RuntimeException("Unsupported backed");
	}
	
	public static BufferTexture createSinglePixelTexture(byte r, byte g, byte b, byte a) {
		return new SinglePixelBufferTexture(r, g, b, a);
	}
	
	public static @Nullable DmabufTexture createDmabufTexture(Dmabuf dmabuf) {
		GpuDeviceBackend deviceBackend = RenderSystem.getDevice().backend;
		try {
			if(deviceBackend instanceof GlDevice) {
				return new GlDmabufTexture(dmabuf);
			}
			
			throw new RuntimeException("Unsupported backed");
		} catch(DmabufImportFailedException e) {
			return null;
		}
	}
	
	private static class SinglePixelBufferTexture extends BufferTexture {
		
		private GpuTexture texture;
		private GpuTextureView textureView = null;
		
		private SinglePixelBufferTexture(byte r, byte g, byte b, byte a) {
			super(1, 1, FORMAT_ARGB8888);
			
			texture = RenderSystem.getDevice().createTexture("buffertexture-" + this.hashCode(), GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, 1, 1, 1, 1);
			textureView = RenderSystem.getDevice().createTextureView(texture);
			
			try(MemoryStack stack = MemoryStack.stackPush()) {
				ByteBuffer data = stack.bytes(r, g, b, a);
				RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, data, NativeImage.Format.RGBA, 0, 0, 0, 0, 1, 1);
			}
		}
		
		@Override
		public GpuTextureView getTextureView() {
			return textureView;
		}
		
		@Override
		public void release() {
			textureView.close();
			texture.close();
			textureView = null;
		}
		
	}
	
	private static class GlShmBufferTexture extends BufferTexture {
		
		public final int id;
		private GlTexture texture;
		private GpuTextureView textureView;
		
		private final long ptr;
		private final int stride;
		
		private GlShmBufferTexture(long ptr, int width, int height, int format, int stride) {
			super(width, height, format);
			
			this.id = GlStateManager._genTexture();
			this.ptr = ptr;
			this.stride = stride;
			
			texture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "buffertexture-" + this.hashCode(), TextureFormat.RGBA8, width, height, 1, 1, id);
			textureView = RenderSystem.getDevice().createTextureView(texture);
			
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
		
		@Override
		public GpuTextureView getTextureView() {
			return textureView;
		}
		
		@Override
		public void release() {
			textureView.close();
			texture.close();
			textureView = null;
		}
		
	}
	
	public static abstract class DmabufTexture extends BufferTexture {
		
		protected RenderTarget target;
		protected GpuTextureView internalView = null;
		
		// Subclass constructor must set internalView!
		private DmabufTexture(Dmabuf buf) throws DmabufImportFailedException {
			super(buf.width(), buf.height(), BufferTexture.FORMAT_ARGB8888);
			
			target = new TextureTarget("dmabuf-target-" + this.hashCode(), width, height, false);
		}
		
		protected abstract void doFree();
		
		// Destroys internal data when backing native dmabuf is gone
		public void freeInternal() {
			if(internalView == null) throw new IllegalStateException("freeInternal() called on non-backed DmabufTexture");
			
			doFree();
			internalView = null;
			
			checkTextureDestroy();
		}
		
		@Override
		public GpuTextureView getTextureView() {
			if(target == null) return null;
			return target.getColorTextureView();
		}
		
		public void copyData() {
			if(internalView == null) throw new IllegalStateException("copyData() called on non-backed DmabufTexture");
			
			try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Dmabuf blit", target.getColorTextureView(), OptionalInt.of(0x00000000))) {
				renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.bindTexture("InSampler", internalView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				renderPass.draw(0, 3);
			}
		}
		
		// Returns true when this object is no longer backed by a dmabuf
		// The textures are still readable and valid until destroyTexture() is called
		public boolean isGone() {
			return internalView == null;
		}
		
		@Override
		public void release() {
			checkTextureDestroy();
		}
		
		// Checks if the renderable textures should be destroyed
		private void checkTextureDestroy() {
			if(target == null) {
				// already destroyed
				return;
			}
			
			// There are two things that need to be true to destroy the textures:
			// 1. The dmabuf has to be gone from native code
			// 2. This texture is no longer attached to any surfaces
			
			if(!isGone()) {
				// dmabuf still present in native code, don't destroy
				return;
			}
			
			for(WLCSurface surface : WaylandCraft.instance.bridge.getAllSurfaces()) {
				if(surface.getBuffer() == this) {
					// still attached, don't destroy textures
					return;
				}
			}
			
			target.destroyBuffers();
			target = null;
		}
		
	}
	
	public static class DmabufImportFailedException extends Exception {
	}
	
	private static class GlDmabufTexture extends DmabufTexture {
		
		private final long eglImage;
		private int eglImageTex = -1;
		
		private GlDmabufTexture(Dmabuf buf) throws DmabufImportFailedException {
			super(buf);
			
			long dpy = EGL.getEGLDisplay();
			eglImage = EGLHelper.importDmabufToImage(dpy, buf);
			if(eglImage == EGL.EGL_NO_IMAGE) {
				WaylandCraftCommon.LOGGER.error("Failed to import dmabuf! EGL error: " + EGL.eglGetErrorString());
				throw new DmabufImportFailedException();
			}
			
			init();
			copyData();
		}
		
		private void init() {
			/* Create texture for EGLImage */
			eglImageTex = GlStateManager._genTexture();
			GlStateManager._bindTexture(eglImageTex);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LEVEL, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_LOD, 0);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAX_LOD, 0);
			
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
			GlStateManager._texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
			
			long glEGLImageTargetTexture2DOES = GLFW.glfwGetProcAddress("glEGLImageTargetTexture2DOES");
			JNI.invokeJV(GL33.GL_TEXTURE_2D, eglImage, glEGLImageTargetTexture2DOES);
			
			GlTexture glTexture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "eglimage-" + this.hashCode(), TextureFormat.RGBA8, width, height, 1, 1, eglImageTex);
			internalView = RenderSystem.getDevice().createTextureView(glTexture);
		}
		
		@Override
		public void doFree() {
			long dpy = EGL.getEGLDisplay();
			EGL.eglDestroyImage(dpy, eglImage);
			
			GlStateManager._deleteTexture(eglImageTex);
		}
		
	}
	
}
