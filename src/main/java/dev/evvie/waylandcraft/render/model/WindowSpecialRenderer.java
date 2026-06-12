package dev.evvie.waylandcraft.render.model;

import java.util.function.Consumer;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

public class WindowSpecialRenderer implements SpecialModelRenderer<Identifier> {
	
	@Override
	public void submit(Identifier icon, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, int overlayCoords, boolean foil, int outlineColor) {
		poseStack.pushPose();
		poseStack.translate(0, 0, 0.5);
		submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.itemTranslucent(icon), new IconRenderer(light, overlayCoords));
		poseStack.popPose();
	}
	
	@Override
	public Identifier extractArgument(ItemStack item) {
		WLCToplevel toplevel = WaylandCraft.getToplevel(item);
		if(toplevel == null) return null;
		
		DesktopEntry entry = WaylandCraft.instance.xdgManager.forAppId(toplevel.appID);
		if(entry == null) return null;
		
		Identifier icon = entry.getIcon();
		return icon;
	}
	
	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		/* I have no clue what coordinate space these are supposed to be but this seems to work I guess */
		Pose pose = new PoseStack().last();
		consumer.accept(pose.pose().transformPosition(0, 1, 0, new Vector3f()));
		consumer.accept(pose.pose().transformPosition(0, 0, 0, new Vector3f()));
		consumer.accept(pose.pose().transformPosition(1, 0, 0, new Vector3f()));
		consumer.accept(pose.pose().transformPosition(1, 1, 0, new Vector3f()));
	}
	
	public static record IconRenderer(int light, int overlayCoords) implements SubmitNodeCollector.CustomGeometryRenderer {
		
		@Override
		public void render(Pose pose, VertexConsumer buffer) {
			Vector3f pos1 = pose.pose().transformPosition(0, 1, 0, new Vector3f());
			Vector3f pos2 = pose.pose().transformPosition(0, 0, 0, new Vector3f());
			Vector3f pos3 = pose.pose().transformPosition(1, 0, 0, new Vector3f());
			Vector3f pos4 = pose.pose().transformPosition(1, 1, 0, new Vector3f());
			
			Vector2f uv1 = new Vector2f(0, 0);
			Vector2f uv2 = new Vector2f(0, 1);
			Vector2f uv3 = new Vector2f(1, 1);
			Vector2f uv4 = new Vector2f(1, 0);
			
			Vector3f normal = pose.transformNormal(0, 0, 1, new Vector3f());
			
			// Front quad
			buffer.addVertex(/* pos */ pos1.x, pos1.y, pos1.z, /* color */ ARGB.white(1.0f), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos2.x, pos2.y, pos2.z, /* color */ ARGB.white(1.0f), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos3.x, pos3.y, pos3.z, /* color */ ARGB.white(1.0f), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos4.x, pos4.y, pos4.z, /* color */ ARGB.white(1.0f), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			
			// Back quad
			buffer.addVertex(/* pos */ pos1.x, pos1.y, pos1.z, /* color */ ARGB.white(1.0f), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos4.x, pos4.y, pos4.z, /* color */ ARGB.white(1.0f), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos3.x, pos3.y, pos3.z, /* color */ ARGB.white(1.0f), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos2.x, pos2.y, pos2.z, /* color */ ARGB.white(1.0f), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		}
		
	}
	
	public static record Unbaked() implements SpecialModelRenderer.Unbaked<Identifier> {
		
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());
		
		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked<Identifier>> type() {
			return MAP_CODEC;
		}
		
		@Override
		public SpecialModelRenderer<Identifier> bake(BakingContext bakingContext) {
			return new WindowSpecialRenderer();
		}
		
	}
	
}
