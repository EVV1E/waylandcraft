package dev.evvie.waylandcraft.render.model;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// Draws a window item as its app icon: a double-sided quad across the item's unit square
public class WindowSpecialRenderer extends BlockEntityWithoutLevelRenderer {
	
	private static final int WHITE = FastColor.ARGB32.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f);
	
	public WindowSpecialRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}
	
	@Override
	public void renderByItem(ItemStack itemStack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlayCoords) {
		ResourceLocation icon = WindowItemModel.getIcon(itemStack);
		if(icon == null) return;
		
		poseStack.pushPose();
		poseStack.translate(0, 0, 0.5);
		renderIcon(poseStack.last(), buffers.getBuffer(RenderType.itemEntityTranslucentCull(icon)), light, overlayCoords);
		poseStack.popPose();
	}
	
	private static void renderIcon(Pose pose, VertexConsumer buffer, int light, int overlayCoords) {
		Vector3f pos1 = pose.pose().transformPosition(0, 1, 0, new Vector3f());
		Vector3f pos2 = pose.pose().transformPosition(0, 0, 0, new Vector3f());
		Vector3f pos3 = pose.pose().transformPosition(1, 0, 0, new Vector3f());
		Vector3f pos4 = pose.pose().transformPosition(1, 1, 0, new Vector3f());
		
		Vector3f normal = pose.transformNormal(0, 0, 1, new Vector3f());
		
		// Front quad
		buffer.addVertex(pos1.x, pos1.y, pos1.z, WHITE, 0, 0, overlayCoords, light, normal.x, normal.y, normal.z);
		buffer.addVertex(pos2.x, pos2.y, pos2.z, WHITE, 0, 1, overlayCoords, light, normal.x, normal.y, normal.z);
		buffer.addVertex(pos3.x, pos3.y, pos3.z, WHITE, 1, 1, overlayCoords, light, normal.x, normal.y, normal.z);
		buffer.addVertex(pos4.x, pos4.y, pos4.z, WHITE, 1, 0, overlayCoords, light, normal.x, normal.y, normal.z);
		
		// Back quad
		buffer.addVertex(pos1.x, pos1.y, pos1.z, WHITE, 0, 0, overlayCoords, light, normal.x, normal.y, normal.z);
		buffer.addVertex(pos4.x, pos4.y, pos4.z, WHITE, 1, 0, overlayCoords, light, normal.x, normal.y, normal.z);
		buffer.addVertex(pos3.x, pos3.y, pos3.z, WHITE, 1, 1, overlayCoords, light, normal.x, normal.y, normal.z);
		buffer.addVertex(pos2.x, pos2.y, pos2.z, WHITE, 0, 1, overlayCoords, light, normal.x, normal.y, normal.z);
	}
	
}
