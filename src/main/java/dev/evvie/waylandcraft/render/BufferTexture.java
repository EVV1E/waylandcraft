package dev.evvie.waylandcraft.render;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.JNI;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf;
import dev.evvie.waylandcraft.egl.EGL;
import dev.evvie.waylandcraft.egl.EGLHelper;
import dev.evvie.waylandcraft.mixin.IGlTextureMixin;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

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
		GpuDeviceBackend deviceBackend = RenderSystem.getDevice().backend;
		if(deviceBackend instanceof GlDevice) {
			return new GlSinglePixelBufferTexture(r, g, b, a);
		}
		
		throw new RuntimeException("Unsupported backed");
	}
	
	public static DmabufTexture createDmabufTexture(long handle, Dmabuf dmabuf) throws DmabufImportFailedException {
		GpuDeviceBackend deviceBackend = RenderSystem.getDevice().backend;
		if(deviceBackend instanceof GlDevice) {
			return new GlDmabufTexture(handle, dmabuf);
		}
		
		throw new RuntimeException("Unsupported backed");
	}
	
	private static abstract class GlBasicBufferTexture extends BufferTexture {
		
		public final int id;
		private GpuTextureView textureView = null;
		
		private GlBasicBufferTexture(int width, int height, int format) {
			super(width, height, format);
			this.id = GlStateManager._genTexture();
			
			GlTexture glTexture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "buffertexture-" + this.hashCode(), GpuFormat.RGBA8_UINT, width, height, 1, 1, id, ((GlDevice) RenderSystem.getDevice().backend).frameBufferCache());
			this.textureView = RenderSystem.getDevice().createTextureView(glTexture);
		}
		
		@Override
		public GpuTextureView getTextureView() {
			return textureView;
		}
		
		@Override
		public void release() {
			textureView = null;
			GlStateManager._deleteTexture(id);
		}
		
	}
	
	private static class GlShmBufferTexture extends GlBasicBufferTexture {
		
		private final long ptr;
		private final int stride;
		
		private GlShmBufferTexture(long ptr, int width, int height, int format, int stride) {
			super(width, height, format);
			this.ptr = ptr;
			this.stride = stride;
			
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
	
	private static class GlSinglePixelBufferTexture extends GlBasicBufferTexture {
		
		public final byte r;
		public final byte g;
		public final byte b;
		public final byte a;
		
		private GlSinglePixelBufferTexture(byte r, byte g, byte b, byte a) {
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
	
	public static final RenderPipeline DMABUF_BLIT = RenderPipelines.register(
		RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/dmabuf_blit"))
			.withVertexShader("core/screenquad")
			.withFragmentShader("core/blit_screen")
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build()
	);
	
	public static abstract class DmabufTexture extends BufferTexture {
		
		public final long handle;
		protected RenderTarget target;
		protected GpuTextureView internalView = null;
		
		private DmabufTexture(long handle, Dmabuf buf) throws DmabufImportFailedException {
			super(buf.width(), buf.height(), BufferTexture.FORMAT_ARGB8888);
			this.handle = handle;
			
			target = new TextureTarget("dmabuf-target-" + this.hashCode(), width, height, false, GpuFormat.RGBA8_UNORM);
		}
		
		// Destroys internal data
		public abstract void doFree();
		
		@Override
		public GpuTextureView getTextureView() {
			if(target == null) return null;
			return target.getColorTextureView();
		}
		
		public void copyData() {
			if(internalView == null) return;
			
			try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Dmabuf blit", target.getColorTextureView(), Optional.of(new Vector4f(0, 0, 0, 0)))) {
				renderPass.setPipeline(DMABUF_BLIT);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.bindTexture("InSampler", internalView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				renderPass.draw(3, 1, 0, 0);
			}
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
		private int eglImageTex = -1;
		
		private GlDmabufTexture(long handle, Dmabuf buf) throws DmabufImportFailedException {
			super(handle, buf);
			
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
			
			GlTexture glTexture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "eglimage-" + this.hashCode(), GpuFormat.RGBA8_UINT, width, height, 1, 1, eglImageTex, ((GlDevice) RenderSystem.getDevice().backend).frameBufferCache());
			internalView = RenderSystem.getDevice().createTextureView(glTexture);
		}
		
		@Override
		public void doFree() {
			if(internalView == null) return;
			
			long dpy = EGL.getEGLDisplay();
			EGL.eglDestroyImage(dpy, eglImage);
			
			GlStateManager._deleteTexture(eglImageTex);
			internalView = null;
		}
		
	}
	
}
