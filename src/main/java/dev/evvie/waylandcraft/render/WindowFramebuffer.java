package dev.evvie.waylandcraft.render;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.OptionalInt;

import org.joml.Matrix4fc;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCSurface.SurfaceDamage;
import dev.evvie.waylandcraft.bridge.WLCSurface.ViewportSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public class WindowFramebuffer {
	
	public static final RenderPipeline WINDOW_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder()
		.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window"))
		.withVertexShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window"))
		.withFragmentShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window"))
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
		.withSampler("sampler")
		.withUniform("window_info", UniformType.UNIFORM_BUFFER)
		.withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA)))
		.withCull(false)
		.build()
	);
	
	public static final RenderPipeline DAMAGE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder()
		.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/damage"))
		.withVertexShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window"))
		.withFragmentShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_damage"))
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
		.withUniform("window_info", UniformType.UNIFORM_BUFFER)
		.withCull(false)
		.build()
	);
	
	private static DynamicUniformStorage<WindowInfoUniform> uniformStorage = null;
	private static boolean debugDamage = false;
	
	public final WLCSurface surfaceTree;
	private RenderTarget target = null;
	private FramebufferTexture texture = null;
	private Identifier location = null;
	
	private int width = 0;
	private int height = 0;
	private int xoff;
	private int yoff;
	
	public WindowFramebuffer(WLCSurface surfaceTree) {
		this.surfaceTree = surfaceTree;
	}
	
	public static void endFrame() {
		if(uniformStorage != null) uniformStorage.endFrame();
	}
	
	private static void ensureUniformStorage() {
		if(uniformStorage == null) {
			uniformStorage = new DynamicUniformStorage<WindowInfoUniform>("window framebuffer", WindowInfoUniform.SIZE, 2);
		}
	}
	
	private void updateTarget() {
		int minX = 0;
		int minY = 0;
		int maxX = 0;
		int maxY = 0;
		
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			int sMinX = surface.xSubpos;
			int sMinY = surface.ySubpos;
			int sMaxX = sMinX + surface.width();
			int sMaxY = sMinY + surface.height();
			
			if(sMinX < minX) minX = sMinX;
			if(sMinY < minY) minY = sMinY;
			if(sMaxX > maxX) maxX = sMaxX;
			if(sMaxY > maxY) maxY = sMaxY;
		}
		
		int prevWidth = width;
		int prevHeight = height;
		
		this.xoff = -minX;
		this.yoff = -minY;
		this.width = maxX - minX;
		this.height = maxY - minY;
		
		if(width <= 0 || height <= 0) {
			destroy();
			return;
		}
		
		if(width != prevWidth || height != prevHeight) destroy();
		
		if(target == null) {
			target = new TextureTarget(name(), width, height, false);
		}
		
		if(texture == null) registerTexture();
	}
	
	private String name() {
		return "wayland-framebuffer-" + this.hashCode() + "-" + surfaceTree.hashCode();
	}
	
	public void render() {
		updateTarget();
		if(target == null) return;
		
		PoseStack poseStack = new PoseStack();
		poseStack.translate(-1.0, -1.0, 0.0);
		poseStack.scale(2.0f / width, 2.0f / height, 1.0f);
		
		ArrayList<CompiledBufferDraw> elements = new ArrayList<>();
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			BufferDraw draw = bakeSurface(surface, xoff + surface.xSubpos, yoff + surface.ySubpos);
			if(draw != null) elements.add(draw.compile());
		}
		
		ensureUniformStorage();
		GpuBufferSlice alphaUniforms = uniformStorage.writeUniform(new WindowInfoUniform(poseStack.last().pose(), true));
		GpuBufferSlice opaqueUniforms = uniformStorage.writeUniform(new WindowInfoUniform(poseStack.last().pose(), false));
		
		try {
			try(RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "window framebuffer", target.getColorTextureView(), OptionalInt.of(0x00000000))) {
				pass.setPipeline(WINDOW_PIPELINE);
				for(CompiledBufferDraw element : elements) {
					pass.setUniform("window_info", element.alpha ? alphaUniforms : opaqueUniforms);
					pass.bindTexture("sampler", element.textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
					pass.setVertexBuffer(0, element.vertexBuffer);
					pass.setIndexBuffer(element.indexBuffer, element.indexType);
					pass.drawIndexed(0, 0, element.indexCount, 1);
				}
			}
		}
		finally {
			for(CompiledBufferDraw element : elements) {
				element.vertexBuffer.close();
			}
		}
		
		if(debugDamage) drawDebugDamage(opaqueUniforms);
	}
	
	private void drawDebugDamage(GpuBufferSlice opaqueUniforms) {
		ArrayList<CompiledBufferDraw> damageElements = new ArrayList<>();
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			int sx = xoff + surface.xSubpos;
			int sy = yoff + surface.ySubpos;
			
			for(SurfaceDamage damage : surface.getDamage()) {
				damageElements.add(new BufferDraw(null, sx + damage.x(), sy + damage.y(), damage.width(), damage.height(), 0, 0, 0, 0, false).compile());
			}
		}
		
		try {
			try(RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "window framebuffer damage", target.getColorTextureView(), OptionalInt.empty())) {
				pass.setPipeline(DAMAGE_PIPELINE);
				pass.setUniform("window_info", opaqueUniforms);
				for(CompiledBufferDraw element : damageElements) {
					pass.setVertexBuffer(0, element.vertexBuffer);
					pass.setIndexBuffer(element.indexBuffer, element.indexType);
					pass.drawIndexed(0, 0, element.indexCount, 1);
				}
			}
		}
		finally {
			for(CompiledBufferDraw element : damageElements) {
				element.vertexBuffer.close();
			}
		}
	}
	
	private BufferDraw bakeSurface(WLCSurface surface, float x, float y) {
		BufferTexture buf = surface.getBuffer();
		if(buf == null) return null;
		
		float w = surface.width();
		float h = surface.height();
		
		float crop_x1 = 0.0f;
		float crop_y1 = 0.0f;
		float crop_x2 = 1.0f;
		float crop_y2 = 1.0f;
		
		ViewportSource src = surface.getViewportSource();
		if(src != null) {
			crop_x1 = (float) (src.x() / buf.width);
			crop_y1 = (float) (src.y() / buf.height);
			crop_x2 = (float) ((src.x() + src.width()) / buf.width);
			crop_y2 = (float) ((src.y() + src.height()) / buf.height);
		}
		
		return new BufferDraw(buf.getTextureView(), x, y, w, h, crop_x1, crop_y1, crop_x2, crop_y2, buf.format != BufferTexture.FORMAT_XRGB8888);
	}
	
	private static record CompiledBufferDraw(GpuTextureView textureView, GpuBuffer vertexBuffer, GpuBuffer indexBuffer, int indexCount, VertexFormat.IndexType indexType, boolean alpha) {
	}
	
	private static record BufferDraw(GpuTextureView textureView, float x, float y, float w, float h, float u1, float v1, float u2, float v2, boolean alpha) {
		
		public CompiledBufferDraw compile() {
			try(ByteBufferBuilder byteBuilder = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4)) {
				BufferBuilder builder = new BufferBuilder(byteBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
				builder.addVertex(x, y, 0).setUv(u1, v1);
				builder.addVertex(x + w, y, 0).setUv(u2, v1);
				builder.addVertex(x + w, y + h, 0).setUv(u2, v2);
				builder.addVertex(x, y + h, 0).setUv(u1, v2);
				
				try(MeshData mesh = builder.buildOrThrow()) {
					int indexCount = mesh.drawState().indexCount();
					RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
					GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(null, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
					GpuBuffer indexBuffer = indices.getBuffer(indexCount);
					return new CompiledBufferDraw(textureView, vertexBuffer, indexBuffer, indexCount, indices.type(), alpha);
				}
			}
		}
		
	}
	
	private void registerTexture() {
		if(target == null) return;
		
		texture = new FramebufferTexture(getTextureView());
		location = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, name());
		
		Minecraft.getInstance().getTextureManager().register(location, texture);
	}
	
	private void unregisterTexture() {
		TextureManager manager = Minecraft.getInstance().getTextureManager();
		manager.register(location, manager.getTexture(MissingTextureAtlasSprite.getLocation()));
		texture = null;
		location = null;
	}
	
	public void destroy() {
		if(target != null) target.destroyBuffers();
		if(texture != null) unregisterTexture();
		target = null;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public int getXOff() {
		return xoff;
	}
	
	public int getYOff() {
		return yoff;
	}
	
	public GpuTextureView getTextureView() {
		if(target == null) return null;
		return target.getColorTextureView();
	}
	
	public Identifier getTextureLocation() {
		return location;
	}
	
	public boolean isValid() {
		return target != null;
	}
	
	private static class FramebufferTexture extends AbstractTexture {
		
		public FramebufferTexture(GpuTextureView textureView) {
			this.textureView = textureView;
			this.texture = textureView.texture();
		}
		
		@Override
		public void close() {
		}
		
	}
	
	private static record WindowInfoUniform(Matrix4fc mat, boolean alpha) implements DynamicUniform {
		
		public static final int SIZE = new Std140SizeCalculator().putMat4f().putFloat().get();
		
		@Override
		public void write(ByteBuffer byteBuffer) {
			Std140Builder.intoBuffer(byteBuffer).putMat4f(mat).putFloat(alpha ? 0.0f : 1.0f);
		}
		
	}
	
}
