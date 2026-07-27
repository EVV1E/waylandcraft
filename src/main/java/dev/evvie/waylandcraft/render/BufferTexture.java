package dev.evvie.waylandcraft.render;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeEGL;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.JNI;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.mixin.IGlDeviceMixin;
import dev.evvie.waylandcraft.mixin.IGlTextureMixin;
import dev.evvie.waylandcraft.mixin.IGpuDeviceMixin;
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
	
	public static abstract class BasicBufferTexture extends BufferTexture {
		
		public final int id;
		private GpuTextureView textureView = null;
		
		public BasicBufferTexture(int width, int height, int format) {
			super(width, height, format);
			this.id = GlStateManager._genTexture();
			GlTexture glTexture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "buffertexture-" + this.hashCode(), GpuFormat.RGBA8_UNORM, width, height, 1, 1, id, ((IGlDeviceMixin) ((IGpuDeviceMixin) RenderSystem.getDevice()).getBackend()).invokeFrameBufferCache());
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
	
	public static class ShmBufferTexture extends BasicBufferTexture {
		
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
	
	public static class SinglePixelBufferTexture extends BasicBufferTexture {
		
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
	
	private static final BindGroupLayout DMABUF_BLIT_SAMPLER_LAYOUT = BindGroupLayout.builder()
		.withSampler("InSampler")
		.build();

	public static final RenderPipeline DMABUF_BLIT = RenderPipelines.register(
		RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/dmabuf_blit"))
			.withVertexShader("core/screenquad")
			.withFragmentShader("core/blit_screen")
			.withBindGroupLayout(DMABUF_BLIT_SAMPLER_LAYOUT)
			.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withCull(false)
			.build()
	);
	
	public static class DmabufTexture extends BufferTexture {
		
		public final long handle;
		private final long eglImage;
		
		private GpuTextureView eglImageView = null;
		private int eglImageTex = -1;
		
		private RenderTarget target;
		
		public DmabufTexture(long handle, long eglImage, int width, int height) {
			super(width, height, BufferTexture.FORMAT_ARGB8888);
			this.handle = handle;
			this.eglImage = eglImage;
			
			target = new TextureTarget("dmabuf-target-" + this.hashCode(), width, height, false, GpuFormat.RGBA8_UNORM);
			
			init();
		}
		
		@Override
		public GpuTextureView getTextureView() {
			if(target == null) return null;
			return target.getColorTextureView();
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
			JNI.invokeJV(GL33.GL_TEXTURE_2D, this.eglImage, glEGLImageTargetTexture2DOES);
			
			GlTexture glTexture = IGlTextureMixin.createTexture(GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, "eglimage-" + this.hashCode(), GpuFormat.RGBA8_UNORM, width, height, 1, 1, eglImageTex, ((IGlDeviceMixin) ((IGpuDeviceMixin) RenderSystem.getDevice()).getBackend()).invokeFrameBufferCache());
			eglImageView = RenderSystem.getDevice().createTextureView(glTexture);
			
			copyData();
		}
		
		public void copyData() {
			if(eglImageView == null) return;
			
			try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Dmabuf blit", target.getColorTextureView(), Optional.of(new Vector4f(0, 0, 0, 0)))) {
				renderPass.setPipeline(DMABUF_BLIT);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.bindTexture("InSampler", eglImageView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				renderPass.draw(0, 3, 1, 0);
			}
		}
		
		@Override
		public void release() {
			// Don't release texture id as dmabuf textures might get reused
		}
		
		public void doReleaseTexure() {
			target.destroyBuffers();
			target = null;
		}
		
		public void freeEGL() {
			if(eglImageView == null) return;
			
			long display = GLFWNativeEGL.glfwGetEGLDisplay();
			long eglDestroyImage = GLFW.glfwGetProcAddress("eglDestroyImage");
			JNI.invokePPI(display, this.eglImage, eglDestroyImage);
			
			GlStateManager._deleteTexture(eglImageTex);
			eglImageView = null;
		}
		
	}
	
}
