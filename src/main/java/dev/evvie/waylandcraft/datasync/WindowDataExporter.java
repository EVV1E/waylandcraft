package dev.evvie.waylandcraft.datasync;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.WindowMetadataPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ArrayListDeque;

public class WindowDataExporter {
	
	public ArrayList<WindowExportState> exports = new ArrayList<WindowExportState>();
	private boolean reset = false;
	
	public void update() {
		if(reset) {
			for(WindowExportState export : exports) {
				export.destroy();
			}
			exports.clear();
			reset = false;
		}
		
		ArrayList<WindowExportState> toRemove = new ArrayList<WindowExportState>();
		for(WindowExportState export : exports) {
			if(!export.window.isAlive()) {
				ClientPlayNetworking.send(new WindowMetadataPayload(export.handle, WindowMetadataPayload.REMOVED, 0, 0, 0, 0));
				export.destroy();
				toRemove.add(export);
			}
		}
		exports.removeAll(toRemove);
		
		for(WindowExportState export : exports) {
			if(!export.metadataDirty) continue;
			
			ClientPlayNetworking.send(new WindowMetadataPayload(export.handle, WindowMetadataPayload.NONE, export.fbWidth, export.fbHeight, export.xoff, export.yoff));
			export.metadataDirty = false;
		}
		
//		for(WindowExportState export : exports) {
//			if(export.data == null) continue;
//			
//			ClientPlayNetworking.send(new WindowDataPayload(export.handle, WindowDataPayload.FORMAT_RAW_RGBA, 0, 0, export.width, export.height, export.data));
//			export.data = null;
//		}
	}
	
	private @Nullable WindowExportState getExport(WLCAbstractWindow window) {
		return exports.stream().filter((e) -> e.window == window).findAny().orElse(null);
	}
	
	private WindowExportState getOrCreateExport(WLCAbstractWindow window) {
		WindowExportState export = getExport(window);
		if(export != null) return export;
		
		export = new WindowExportState(window);
		exports.add(export);
		return export;
	}
	
	public void export(WLCAbstractWindow window) {
		WindowExportState export = getOrCreateExport(window);
//		if(!export.copyFramebufferTexture()) return;
//		export.readTarget();
	}
	
	public void reset() {
		reset = true;
	}
	
	public static class PatchTexture {
		
		private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
		
		public final GpuTexture texture;
		public final int width;
		public final int height;
		
		public PatchTexture(String name, int width, int height) {
			this.width = width;
			this.height = height;
			this.texture = RenderSystem.getDevice().createTexture(name, TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
		}
		
	}
	
	public static class WindowExportState {
		
		public final WLCAbstractWindow window;
		public final WindowHandle handle;
//		public GpuTexture target = null;
		
		public int fbWidth = 0;
		public int fbHeight = 0;
		public int xoff = 0;
		public int yoff = 0;
		
		public boolean metadataDirty = true;
		
		public static final int MAX_SIZE = 2048;
		
		public ArrayListDeque<ImagePatch> patches = new ArrayListDeque<>();
		
		public WindowExportState(WLCAbstractWindow window) {
			this.window = window;
			this.handle = WindowHandle.forPlayer(Minecraft.getInstance().player, window.getHandle());
		}
		
		public boolean updateMetadata() {
			if(window.framebuffer == null || !window.framebuffer.isValid()) return false;
			
			int width = window.framebuffer.getWidth();
			int height = window.framebuffer.getHeight();
			int xoff = window.framebuffer.getXOff() + window.geometry.x();
			int yoff = window.framebuffer.getYOff() + window.geometry.y();
			
			if(xoff != this.xoff || yoff != this.yoff || width != this.fbWidth || height != this.fbHeight) {
				this.metadataDirty = true;
				this.xoff = xoff;
				this.yoff = yoff;
				this.fbWidth = width;
				this.fbHeight = height;
			}
			
			return true;
		}
		
		public void destroy() {
		}
		
//		public boolean copyFramebufferTexture() {
//			GpuTexture tex = window.framebuffer.getTexture();
//			if(tex == null) return false;
//			if(width > MAX_SIZE || height > MAX_SIZE) return false;
//			
//			if(this.width != width || this.height != height || target == null) {
//				this.width = width;
//				this.height = height;
//				this.metadataDirty = true;
//				if(target != null) target.close();
//				target = RenderSystem.getDevice().createTexture("export-" + window.framebuffer.getName(), TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
//			}
//			
//			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(tex, target, 0, 0, 0, 0, 0, width, height);
//			
//			return true;
//		}
		
//		public void readTarget() {
//			this.data = null;
//			
//			if(target == null) return;
//			
//			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "export-" + window.framebuffer.getName(), GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, width * height * 4);
//			RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(target, buffer, 0l, () -> {
//				try(GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
//					this.data = new byte[view.data().remaining()];
//					view.data().get(this.data);
//				}
//				buffer.close();
//			}, 0);
//		}
		
//		public void destroy() {
//			target.close();
//		}
		
	}
	
}
