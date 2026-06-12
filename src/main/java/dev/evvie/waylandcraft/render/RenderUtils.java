package dev.evvie.waylandcraft.render;

import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.mixin.IGuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

public class RenderUtils {
	
	private static final RenderPipeline.Snippet WINDOW_PIPELINE_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withVertexShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "core/rendertype_window"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "core/rendertype_window"))
			.withSampler("Sampler0")
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
			.buildSnippet();
	
	private static final RenderPipeline WINDOW_CUTOUT_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window_cutout"))
			.withShaderDefine("ALPHA_CUTOUT")
			.build();
	
	private static final RenderPipeline WINDOW_TRANSLUCENT_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window_translucent"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build();
	
	private static final RenderPipeline WINDOW_CUTOUT_ANTIALIASING_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window_cutout"))
			.withShaderDefine("ALPHA_CUTOUT")
			.withShaderDefine("RGSS")
			.build();
	
	private static final RenderPipeline WINDOW_TRANSLUCENT_ANTIALIASING_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window_translucent"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withShaderDefine("RGSS")
			.build();
	
	private static final RenderPipeline WINDOW_CUTOUT_BACKGROUND_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window_cutout_background"))
			.withShaderDefine("ALPHA_CUTOUT")
			.withShaderDefine("NO_COLOR")
			.build();
	
	private static final RenderPipeline WINDOW_TRANSLUCENT_BACKGROUND_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window_translucent_background"))
			.withShaderDefine("NO_COLOR")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build();
	
	public static final Supplier<GpuSampler> WINDOW_SAMPLER = () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, false);
	
	public static final Function<Identifier, RenderType> WINDOW_CUTOUT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_CUTOUT_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_cutout", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_TRANSLUCENT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_TRANSLUCENT_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_translucent", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_CUTOUT_ANTIALIAS = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_CUTOUT_ANTIALIASING_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_cutout_antialias", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_TRANSLUCENT_ANTIALIAS = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_TRANSLUCENT_ANTIALIASING_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_translucent_antialias", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_BACKGROUND_CUTOUT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_CUTOUT_BACKGROUND_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_cutout_background", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_BACKGROUND_TRANSLUCENT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_TRANSLUCENT_BACKGROUND_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_translucent_background", setup);
		}
	);
	
	public static final RenderPipeline WINDOW_BLIT = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/window_blit"))
			.withVertexShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "core/window_blit"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "core/window_blit"))
			.withSampler("Sampler0")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
			.build();
	
	public static void renderFramebuffer(WindowFramebuffer framebuffer, PoseStack poseStack, SubmitNodeCollector collector, boolean cutout, Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr) {
		if(!framebuffer.isValid()) return;
		
		Function<Identifier, RenderType> renderType;
		
		// Front quad
		if(WaylandCraft.instance.settings.getAntialiasing()) renderType = cutout ? WINDOW_CUTOUT_ANTIALIAS : WINDOW_TRANSLUCENT_ANTIALIAS;
		else renderType = cutout ? WINDOW_CUTOUT : WINDOW_TRANSLUCENT;
		collector.submitCustomGeometry(poseStack, renderType.apply(framebuffer.getTextureLocation()), new FramebufferRenderInstance(tl, bl, br, tr, false));
		
		// Back quad
		renderType = cutout ? WINDOW_BACKGROUND_CUTOUT : WINDOW_BACKGROUND_TRANSLUCENT;
		collector.submitCustomGeometry(poseStack, renderType.apply(framebuffer.getTextureLocation()), new FramebufferRenderInstance(tl, bl, br, tr, true));
	}
	
	public static final record FramebufferRenderInstance(Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr, boolean reverse) implements CustomGeometryRenderer {
		
		@Override
		public void render(Pose pose, VertexConsumer buffer) {
			if(!reverse) {
				buffer.addVertex(pose, tl.toVector3f()).setUv(0.0f, 0.0f);
				buffer.addVertex(pose, bl.toVector3f()).setUv(0.0f, 1.0f);
				buffer.addVertex(pose, br.toVector3f()).setUv(1.0f, 1.0f);
				buffer.addVertex(pose, tr.toVector3f()).setUv(1.0f, 0.0f);
			}
			else {
				buffer.addVertex(pose, tr.toVector3f()).setUv(1.0f, 0.0f);
				buffer.addVertex(pose, br.toVector3f()).setUv(1.0f, 1.0f);
				buffer.addVertex(pose, bl.toVector3f()).setUv(0.0f, 1.0f);
				buffer.addVertex(pose, tl.toVector3f()).setUv(0.0f, 0.0f);
			}
		}
		
	}
	
	public static void renderFramebuffer2D(GuiGraphicsExtractor context, WindowFramebuffer framebuffer, int x, int y, int w, int h) {
		if(!framebuffer.isValid()) return;
		((IGuiGraphicsExtractor) context).invokeInnerBlit(WINDOW_BLIT, framebuffer.getTextureLocation(), x, x + w, y, y + h, 0.0f, 1.0f, 0.0f, 1.0f, -1);
	}
	
}
