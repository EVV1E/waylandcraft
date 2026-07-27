package dev.evvie.waylandcraft.render;

import java.util.Optional;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class WindowTranslucencyHotfix {

	private static final RenderPipeline TRANSLUCENCY_HOTFIX_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "pipeline/translucency_hotfix"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "core/singlecolor"))
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALPHA))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withShaderDefine("RED", 1.0f)
			.withShaderDefine("GREEN", 1.0f)
			.withShaderDefine("BLUE", 1.0f)
			.withShaderDefine("ALPHA", 1.0f)
			.build()
	);

	public static void render() {
		if(Minecraft.getInstance().level == null) return;

		Optional<org.joml.Vector4fc> clearColor = Optional.empty();
		try(RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "translucency_hotfix", Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), clearColor)) {
			pass.setPipeline(TRANSLUCENCY_HOTFIX_PIPELINE);
			pass.draw(0, 3, 1, 0);
		}
	}

}
