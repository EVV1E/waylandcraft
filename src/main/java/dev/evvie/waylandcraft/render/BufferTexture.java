package dev.evvie.waylandcraft.render;

import java.util.OptionalInt;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.JNI;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.dmabuf.Dmabuf;
import dev.evvie.waylandcraft.egl.EGL;
import dev.evvie.waylandcraft.egl.EGLHelper;
import dev.evvie.waylandcraft.mixin.IGlTextureMixin;
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
		return new SinglePixelBufferTexture(r, g, b, a);
	}
	
	public static DmabufTexture createDmabufTexture(Dmabuf dmabuf) throws DmabufImportFailedException {
		GpuDeviceBackend deviceBackend = RenderSystem.getDevice().backend;
		if(deviceBackend instanceof GlDevice) {
			return new GlDmabufTexture(dmabuf);
		}
		
		throw new RuntimeException("Unsupported backed");
	}
	
	private static class SinglePixelBufferTexture extends BufferTexture {
		
		private GpuTexture texture;
		private GpuTextureView textureView = null;
		
		private SinglePixelBufferTexture(byte r, byte g, byte b, byte a) {
			super(1, 1, FORMAT_ARGB8888);
			
			texture = RenderSystem.getDevice().createTexture("buffertexture-" + this.hashCode(), GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.RGBA8, 1, 1, 1, 1);
			textureView = RenderSystem.getDevice().createTextureView(texture);
			
			int c1 = Byte.toUnsignedInt(r);
			int c2 = Byte.toUnsignedInt(g);
			int c3 = Byte.toUnsignedInt(b);
			int c4 = Byte.toUnsignedInt(a);
			int color = c4 << 24 | c1 << 16 | c2 << 8 | c3;
			
			RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, color);
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
	
	private static abstract class GlBasicBufferTexture extends BufferTexture {
		
		public final int id;
		private GlTexture texture;
		private GpuTextureView textureView;
		
		private GlBasicBufferTexture(int width, int height, int format) {
			super(width, height, format);
			this.id = GlStateManager._genTexture();
			
			texture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "buffertexture-" + this.hashCode(), TextureFormat.RGBA8, width, height, 1, 1, id);
			textureView = RenderSystem.getDevice().createTextureView(texture);
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
	
	public static final RenderPipeline DMABUF_BLIT = RenderPipelines.register(
		RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/dmabuf_blit"))
			.withVertexShader("core/screenquad")
			.withFragmentShader("core/blit_screen")
			.withSampler("InSampler")
			.withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA)))
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.build()
	);
	
	public static abstract class DmabufTexture extends BufferTexture {
		
		public final long handle;
		protected RenderTarget target;
		protected GpuTextureView internalView = null;
		
		private DmabufTexture(Dmabuf buf) throws DmabufImportFailedException {
			super(buf.width(), buf.height(), BufferTexture.FORMAT_ARGB8888);
			this.handle = buf.handle();
			
			target = new TextureTarget("dmabuf-target-" + this.hashCode(), width, height, false);
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
			
			try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Dmabuf blit", target.getColorTextureView(), OptionalInt.of(0x00000000))) {
				renderPass.setPipeline(DMABUF_BLIT);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.bindTexture("InSampler", internalView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				renderPass.draw(0, 3);
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
			if(internalView == null) return;
			
			long dpy = EGL.getEGLDisplay();
			EGL.eglDestroyImage(dpy, eglImage);
			
			GlStateManager._deleteTexture(eglImageTex);
			internalView = null;
		}
		
	}
	
}
