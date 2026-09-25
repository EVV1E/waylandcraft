package dev.evvie.waylandcraft.render;

import java.io.IOException;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

// Offscreen compositing shaders (shaders/core/*.json) and the quad draw they share.
// 1.21.1 replacement for the RenderPipelines used by WindowFramebuffer and BufferTexture.
public class WindowShaders {
	
	public static ShaderInstance window;
	public static ShaderInstance unpremultiply;
	public static ShaderInstance damage;
	
	public static void register(RegisterShadersEvent event) {
		try {
			event.registerShader(create(event, "window"), (shader) -> window = shader);
			event.registerShader(create(event, "unpremultiply"), (shader) -> unpremultiply = shader);
			event.registerShader(create(event, "window_damage"), (shader) -> damage = shader);
		} catch(IOException e) {
			throw new RuntimeException("Failed to load WaylandCraft shaders", e);
		}
	}
	
	private static ShaderInstance create(RegisterShadersEvent event, String name) throws IOException {
		return new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, name), DefaultVertexFormat.POSITION_TEX);
	}
	
	// Binds target for writing and optionally clears it to transparent black
	public static void beginTarget(RenderTarget target, boolean clear) {
		target.bindWrite(true);
		if(clear) {
			RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
			RenderSystem.clear(0x4000 /* GL_COLOR_BUFFER_BIT */, Minecraft.ON_OSX);
		}
	}
	
	// Restores the main framebuffer after offscreen rendering
	public static void endTargets() {
		Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
	}
	
	// Blending: premultiplied alpha (ONE, ONE_MINUS_SRC_ALPHA) when blend is set, else overwrite
	public static void drawQuad(ShaderInstance shader, int textureId, Matrix4f transform, boolean blend, float alphaBlend, float x, float y, float w, float h, float u1, float v1, float u2, float v2) {
		shader.safeGetUniform("Transform").set(transform);
		shader.safeGetUniform("AlphaBlend").set(alphaBlend);
		RenderSystem.setShader(() -> shader);
		if(textureId >= 0) RenderSystem.setShaderTexture(0, textureId);
		
		if(blend) {
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(1 /* GL_ONE */, 0x0303 /* GL_ONE_MINUS_SRC_ALPHA */);
		}
		else {
			RenderSystem.disableBlend();
		}
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		
		BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		builder.addVertex(x, y, 0).setUv(u1, v1);
		builder.addVertex(x + w, y, 0).setUv(u2, v1);
		builder.addVertex(x + w, y + h, 0).setUv(u2, v2);
		builder.addVertex(x, y + h, 0).setUv(u1, v2);
		BufferUploader.drawWithShader(builder.buildOrThrow());
		
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
	}
	
	// Draws textureId over the whole bound target (normalized device coordinates)
	public static void drawFullscreen(ShaderInstance shader, int textureId, boolean blend) {
		drawQuad(shader, textureId, new Matrix4f(), blend, 0.0f, -1.0f, -1.0f, 2.0f, 2.0f, 0.0f, 0.0f, 1.0f, 1.0f);
	}
	
}
