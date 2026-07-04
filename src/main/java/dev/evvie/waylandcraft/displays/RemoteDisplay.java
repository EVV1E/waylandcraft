package dev.evvie.waylandcraft.displays;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.render.RemoteFramebuffer;
import dev.evvie.waylandcraft.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public class RemoteDisplay extends AbstractWindowDisplay {
	
	public final WindowHandle handle;
	
	private final RemoteDisplayRenderState renderState = new RemoteDisplayRenderState();
	private Vec3 packetPivot = pivot;
	private Vec3 prevPivot = pivot;
	private Vec3 packetNormal = normal;
	private Vec3 prevNormal = normal;
	private Vec3 packetDown = down;
	private Vec3 prevDown = down;
	
	private int lerpSteps = 0;
	
	public RemoteDisplay(WindowHandle handle) {
		this.handle = handle;
	}
	
	@Override
	public boolean isValid() {
		ChunkPos pos = this.getChunkPos();
		boolean chunkExists = Minecraft.getInstance().level.getChunkSource().getChunk(pos.x(), pos.z(), false) != null;
		return chunkExists;
	}
	
	public void setSize(int width, int height) {
		this.width = width;
		this.height = height;
	}
	
	@Override
	public void tick() {
		prevPivot = pivot;
		prevNormal = normal;
		prevDown = down;
		
		if(lerpSteps > 0) {
			pivot = pivot.lerp(packetPivot, 1.0 / lerpSteps);
			lerpSteps--;
		}
		else {
			pivot = packetPivot;
		}
		
		normal = packetNormal;
		down = packetDown;
	}
	
	@Override
	public void extract(LevelExtractionContext ctx) {
		float partialTicks = ctx.deltaTracker().getGameTimeDeltaPartialTick(true);
		renderState.pivot = this.prevPivot.lerp(this.pivot, partialTicks);
		
		Vec3 normal = this.prevNormal.lerp(this.normal, partialTicks).normalize();
		Vec3 down = this.prevDown.lerp(this.down, partialTicks).normalize();
		
		Vec3 localX = normal.cross(down).scale(pixelScale);
		Vec3 localY = down.scale(pixelScale);
		
		renderState.localX = localX;
		renderState.localY = localY;
	}
	
	@Override
	public void render(LevelRenderContext ctx) {
		if(!WaylandCraft.instance.debugRemoteDisplays) return;
		
		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		
		PoseStack poseStack = ctx.poseStack();
		poseStack.pushPose();
		poseStack.translate(Vec3.ZERO.subtract(cameraPos));
		
		Vec3 pivot = renderState.pivot;
		Vec3 localX = renderState.localX;
		Vec3 localY = renderState.localY;
		Vec3 origin = pivot.subtract(localX.scale(width / 2)).subtract(localY.scale(height / 2));
		
		RenderUtils.renderBox(poseStack, ctx.submitNodeCollector(), pivot, 0xffffffff, 0.07f);
		
		Vec3 spanX = localX.scale(width);
		Vec3 spanY = localY.scale(height);
		Vec3[] points = new Vec3[] {
				origin,
				origin.add(spanX),
				origin.add(spanX).add(spanY),
				origin.add(spanY),
				origin
		};
		RenderUtils.renderLineStrip(poseStack, ctx.submitNodeCollector(), points, 0xffffffff, 2.0f);
		
		RemoteFramebuffer framebuffer = WaylandCraft.instance.windowDataImporter.getFramebuffer(handle);
		if(framebuffer != null) {
			origin = origin.subtract(localX.scale(framebuffer.xoff)).subtract(localY.scale(framebuffer.yoff));
			spanX = localX.scale(framebuffer.width);
			spanY = localY.scale(framebuffer.height);
			RenderUtils.renderFramebuffer(framebuffer.getTextureLocation(), poseStack, ctx.submitNodeCollector(), true, origin, spanX, spanY);
		}
		
		poseStack.popPose();
	}
	
	public void applyProperties(DisplayProperties properties, boolean immediate) {
		if(immediate) {
			this.pivot = this.packetPivot = properties.pivot();
			this.normal = this.packetNormal = properties.normal();
			this.down = this.packetDown = properties.down();
			this.lerpSteps = 0;
		}
		else {
			if(lerpSteps == 0) {
				this.pivot = this.packetPivot;
				this.normal = this.packetNormal;
				this.down = this.packetDown;
			}
			
			this.packetPivot = properties.pivot();
			this.packetNormal = properties.normal();
			this.packetDown = properties.down();
			
			this.lerpSteps = 3;
		}
		
		this.width = properties.width();
		this.height = properties.height();
		this.pixelScale = properties.pixelScale();
	}
	
	private static class RemoteDisplayRenderState {
		
		public Vec3 pivot = Vec3.ZERO;
		public Vec3 localX = Vec3.ZERO;
		public Vec3 localY = Vec3.ZERO;
		
	}
	
}
