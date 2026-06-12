package dev.evvie.waylandcraft.render;

import java.util.Optional;
import java.util.OptionalInt;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

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
			.withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALPHA))
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withShaderDefine("RED", 1.0f)
			.withShaderDefine("GREEN", 1.0f)
			.withShaderDefine("BLUE", 1.0f)
			.withShaderDefine("ALPHA", 1.0f)
			.build()
	);
	
	public static void render() {
		if(Minecraft.getInstance().level == null) return;
		
		OptionalInt clearColor = OptionalInt.empty();
		try(RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "translucency_hotfix", Minecraft.getInstance().getMainRenderTarget().getColorTextureView(), clearColor)) {
			pass.setPipeline(TRANSLUCENCY_HOTFIX_PIPELINE);
			pass.draw(0, 3);
		}
	}
	
}
