package dev.evvie.waylandcraft.render;

import java.io.IOException;
import java.util.function.Function;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.compat.IrisCompat;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public class RenderUtils {
	
	private static ShaderInstance cutoutAntialiasShader;
	private static ShaderInstance translucentAntialiasShader;
	private static ShaderInstance cutoutBackgroundShader;
	private static ShaderInstance translucentBackgroundShader;
	
	public static void registerShaders(RegisterShadersEvent event) {
		try {
			event.registerShader(createShader(event, "rendertype_window_cutout_antialias"), (shader) -> cutoutAntialiasShader = shader);
			event.registerShader(createShader(event, "rendertype_window_translucent_antialias"), (shader) -> translucentAntialiasShader = shader);
			event.registerShader(createShader(event, "rendertype_window_cutout_background"), (shader) -> cutoutBackgroundShader = shader);
			event.registerShader(createShader(event, "rendertype_window_translucent_background"), (shader) -> translucentBackgroundShader = shader);
		} catch(IOException e) {
			throw new RuntimeException("Failed to load WaylandCraft shaders", e);
		}
	}
	
	private static ShaderInstance createShader(RegisterShadersEvent event, String name) throws IOException {
		return new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, name), DefaultVertexFormat.POSITION_TEX);
	}
	
	public static final Function<ResourceLocation, RenderType> WINDOW_CUTOUT_ANTIALIAS = Util.memoize(
		(location) -> WindowRenderTypes.create("window_cutout_antialias", location, () -> cutoutAntialiasShader, false)
	);
	
	public static final Function<ResourceLocation, RenderType> WINDOW_TRANSLUCENT_ANTIALIAS = Util.memoize(
		(location) -> WindowRenderTypes.create("window_translucent_antialias", location, () -> translucentAntialiasShader, true)
	);
	
	public static final Function<ResourceLocation, RenderType> WINDOW_BACKGROUND_CUTOUT = Util.memoize(
		(location) -> WindowRenderTypes.create("window_cutout_background", location, () -> cutoutBackgroundShader, false)
	);
	
	public static final Function<ResourceLocation, RenderType> WINDOW_BACKGROUND_TRANSLUCENT = Util.memoize(
		(location) -> WindowRenderTypes.create("window_translucent_background", location, () -> translucentBackgroundShader, true)
	);
	
	// Subclass only to reach RenderStateShard's protected shard constants
	private static abstract class WindowRenderTypes extends RenderType {
		
		private WindowRenderTypes() {
			super(null, null, null, 0, false, false, null, null);
		}
		
		static RenderType create(String name, ResourceLocation texture, java.util.function.Supplier<ShaderInstance> shader, boolean translucent) {
			RenderType.CompositeState state = RenderType.CompositeState.builder()
					.setShaderState(new RenderStateShard.ShaderStateShard(shader))
					// The texture sets its own filtering (WindowFramebuffer.FramebufferTexture)
					.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
					.setTransparencyState(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
					.setCullState(CULL)
					.createCompositeState(false);
			return RenderType.create(name, DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, translucent, state);
		}
		
	}
	
	public static void renderFramebuffer(WindowFramebuffer framebuffer, PoseStack poseStack, MultiBufferSource buffers, boolean cutout, Vec3 origin, Vec3 spanX, Vec3 spanY) {
		if(!framebuffer.isValid()) return;
		
		// Shader packs replace custom shaders, so use a vanilla entity render type they support:
		// the window texture on the front, a black silhouette on the back
		if(IrisCompat.isShaderActive()) {
			VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(framebuffer.getTextureLocation()));
			addFramebufferQuadEntity(poseStack.last(), buffer, origin, spanX, spanY, FastColor.ARGB32.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f), false);
			addFramebufferQuadEntity(poseStack.last(), buffer, origin, spanX, spanY, FastColor.ARGB32.colorFromFloat(1.0f, 0.0f, 0.0f, 0.0f), true);
			return;
		}
		
		// Front quad
		Function<ResourceLocation, RenderType> renderType = cutout ? WINDOW_CUTOUT_ANTIALIAS : WINDOW_TRANSLUCENT_ANTIALIAS;
		addFramebufferQuad(poseStack.last(), buffers.getBuffer(renderType.apply(framebuffer.getTextureLocation())), origin, spanX, spanY, false);
		
		// Back quad
		renderType = cutout ? WINDOW_BACKGROUND_CUTOUT : WINDOW_BACKGROUND_TRANSLUCENT;
		addFramebufferQuad(poseStack.last(), buffers.getBuffer(renderType.apply(framebuffer.getTextureLocation())), origin, spanX, spanY, true);
	}
	
	private static void addFramebufferQuad(Pose pose, VertexConsumer buffer, Vec3 origin, Vec3 spanX, Vec3 spanY, boolean reverse) {
		Vec3 tl = origin;
		Vec3 bl = tl.add(spanY);
		Vec3 br = bl.add(spanX);
		Vec3 tr = tl.add(spanX);
		
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
	
	private static void addFramebufferQuadEntity(Pose pose, VertexConsumer buffer, Vec3 origin, Vec3 spanX, Vec3 spanY, int color, boolean reverse) {
		Vec3 tl = origin;
		Vec3 bl = tl.add(spanY);
		Vec3 br = bl.add(spanX);
		Vec3 tr = tl.add(spanX);
		Vector3f normal = pose.transformNormal(spanY.cross(spanX).normalize().toVector3f(), new Vector3f());
		
		Vector3f p1 = pose.pose().transformPosition(tl.toVector3f());
		Vector3f p2 = pose.pose().transformPosition(bl.toVector3f());
		Vector3f p3 = pose.pose().transformPosition(br.toVector3f());
		Vector3f p4 = pose.pose().transformPosition(tr.toVector3f());
		
		int overlay = OverlayTexture.NO_OVERLAY;
		int light = LightTexture.FULL_BRIGHT;
		
		if(!reverse) {
			buffer.addVertex(p1.x, p1.y, p1.z, color, 0.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(p2.x, p2.y, p2.z, color, 0.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(p3.x, p3.y, p3.z, color, 1.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(p4.x, p4.y, p4.z, color, 1.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
		}
		else {
			buffer.addVertex(p4.x, p4.y, p4.z, color, 1.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(p3.x, p3.y, p3.z, color, 1.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(p2.x, p2.y, p2.z, color, 0.0f, 1.0f, overlay, light, normal.x, normal.y, normal.z);
			buffer.addVertex(p1.x, p1.y, p1.z, color, 0.0f, 0.0f, overlay, light, normal.x, normal.y, normal.z);
		}
	}
	
	public static void renderFramebuffer2D(GuiGraphics context, WindowFramebuffer framebuffer, int x, int y, int w, int h) {
		if(!framebuffer.isValid()) return;
		blitFull(context, framebuffer.getTextureLocation(), x, y, x + w, y + h);
	}
	
	// Draws the whole texture into the rectangle (x0, y0)-(x1, y1) with alpha blending
	public static void blitFull(GuiGraphics context, ResourceLocation texture, int x0, int y0, int x1, int y1) {
		int w = x1 - x0;
		int h = y1 - y0;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		// u/v spans equal the texture size, so the full texture is mapped
		context.blit(texture, x0, y0, w, h, 0.0f, 0.0f, w, h, w, h);
		RenderSystem.disableBlend();
	}
	
	// Line width is fixed in 1.21.1's lines render type
	public static void renderLineStrip(PoseStack poseStack, MultiBufferSource buffers, Vec3[] points, int color) {
		VertexConsumer buffer = buffers.getBuffer(RenderType.lines());
		Pose pose = poseStack.last();
		for(int i = 1; i < points.length; i++) {
			Vec3 start = points[i - 1];
			Vec3 end = points[i];
			Vector3f normal = end.subtract(start).toVector3f().normalize();
			
			buffer.addVertex(pose, start.toVector3f()).setColor(color).setNormal(pose, normal.x, normal.y, normal.z);
			buffer.addVertex(pose,   end.toVector3f()).setColor(color).setNormal(pose, normal.x, normal.y, normal.z);
		}
	}
	
}
