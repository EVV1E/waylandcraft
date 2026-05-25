package dev.evvie.waylandcraft.render;

import java.util.function.Function;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.evvie.waylandcraft.mixin.IGuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

public class RenderUtils {

  public static final Function<Identifier, RenderType> WINDOW_CUTOUT =
    Util.memoize(id -> RenderTypes.entityCutout(id));

  public static final Function<Identifier, RenderType> WINDOW_TRANSLUCENT =
      Util.memoize(id -> RenderTypes.entityTranslucent(id));

	public static final RenderPipeline WINDOW_BLIT = RenderPipelines.GUI_TEXTURED;

	public static void renderFramebuffer(WindowFramebuffer framebuffer, PoseStack poseStack, SubmitNodeCollector collector, boolean cutout, Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr) {
		if(!framebuffer.isValid()) return;
     collector.submitCustomGeometry(poseStack,
         cutout ? WINDOW_CUTOUT.apply(framebuffer.getTextureLocation())
                : WINDOW_TRANSLUCENT.apply(framebuffer.getTextureLocation()),
        new FramebufferRenderInstance(tl, bl, br, tr));
	}

	public static final record FramebufferRenderInstance(Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr) implements CustomGeometryRenderer {

		@Override
		public void render(Pose pose, VertexConsumer buffer) {
      // Front face
      buffer.addVertex(pose, tl.toVector3f())
          .setColor(1.0f, 1.0f, 1.0f, 1.0f)
          .setUv(0.0f, 0.0f)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(0xF000F0)
          .setNormal(pose, 0.0f, 0.0f, 1.0f);
      buffer.addVertex(pose, bl.toVector3f())
          .setColor(1.0f, 1.0f, 1.0f, 1.0f)
          .setUv(0.0f, 1.0f)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(0xF000F0)
          .setNormal(pose, 0.0f, 0.0f, 1.0f);
      buffer.addVertex(pose, br.toVector3f())
          .setColor(1.0f, 1.0f, 1.0f, 1.0f)
          .setUv(1.0f, 1.0f)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(0xF000F0)
          .setNormal(pose, 0.0f, 0.0f, 1.0f);
      buffer.addVertex(pose, tr.toVector3f())
          .setColor(1.0f, 1.0f, 1.0f, 1.0f)
          .setUv(1.0f, 0.0f)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(0xF000F0)
          .setNormal(pose, 0.0f, 0.0f, 1.0f);
      // Back face — same positions reversed, normal flipped, color black
      buffer.addVertex(pose, tr.toVector3f())
          .setColor(0.0f, 0.0f, 0.0f, 1.0f)
          .setUv(1.0f, 0.0f)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(0xF000F0)
          .setNormal(pose, 0.0f, 0.0f, -1.0f);
		}
	}

	public static void renderFramebuffer2D(GuiGraphicsExtractor context, WindowFramebuffer framebuffer, int x, int y, int w, int h) {
		if(!framebuffer.isValid()) return;
		((IGuiGraphicsExtractor) context).invokeInnerBlit(WINDOW_BLIT, framebuffer.getTextureLocation(), x, x + w, y, y + h, 0.0f, 1.0f, 0.0f, 1.0f, -1);
	}

}
