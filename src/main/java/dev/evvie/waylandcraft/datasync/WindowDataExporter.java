package dev.evvie.waylandcraft.datasync;

import java.util.ArrayDeque;
import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.WindowDataPayload;
import net.minecraft.client.Minecraft;

public class WindowDataExporter {
	
	public ArrayList<WindowExportState> exports = new ArrayList<WindowExportState>();
	public ArrayDeque<WindowDataPayload> payloads = new ArrayDeque<WindowDataPayload>();
	
	public void update() {
		ArrayList<WindowExportState> toRemove = new ArrayList<WindowExportState>();
		for(WindowExportState export : exports) {
			if(!export.window.isAlive()) {
				export.destroy();
				toRemove.add(export);
			}
		}
		exports.removeAll(toRemove);
		
		for(WindowExportState export : exports) {
			if(export.data == null) return; // No new data
			
			WindowHandle handle = WindowHandle.forPlayer(Minecraft.getInstance().player, export.window.getHandle());
			payloads.add(new WindowDataPayload(handle, export.width, export.height, export.xoff, export.yoff, export.data));
			
			export.data = null;
		}
	}
	
	private @Nullable WindowExportState getExport(WLCAbstractWindow window) {
		return exports.stream().filter((e) -> e.window == window).findAny().orElse(null);
	}
	
	// NOTE: Doesn't add the export to the list yet
	private WindowExportState getOrCreateExport(WLCAbstractWindow window) {
		WindowExportState export = getExport(window);
		if(export != null) return export;
		
		export = new WindowExportState(window);
		exports.add(export);
		return export;
	}
	
	public void export(WLCAbstractWindow window) {
		WindowExportState export = getOrCreateExport(window);
		if(!export.copyFramebufferTexture()) return;
		export.readTarget();
	}
	
	public static class WindowExportState {
		
		public final WLCAbstractWindow window;
		public GpuTexture target = null;
		public int width = 0;
		public int height = 0;
		public int xoff = 0;
		public int yoff = 0;
		
		public static final int MAX_SIZE = 2048;
		private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
		
		public byte[] data = null;
		
		public WindowExportState(WLCAbstractWindow window) {
			this.window = window;
		}
		
		public boolean copyFramebufferTexture() {
			if(window.framebuffer == null || !window.framebuffer.isValid()) return false;
			
			int width = window.framebuffer.getWidth();
			int height = window.framebuffer.getHeight();
			this.xoff = window.framebuffer.getXOff() + window.geometry.x();
			this.yoff = window.framebuffer.getYOff() + window.geometry.y();
			
			GpuTexture tex = window.framebuffer.getTexture();
			if(tex == null) return false;
			if(width > MAX_SIZE || height > MAX_SIZE) return false;
			
			if(this.width != width || this.height != height || target == null) {
				this.width = width;
				this.height = height;
				if(target != null) target.close();
				target = RenderSystem.getDevice().createTexture("export-" + window.framebuffer.getName(), TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
			}
			
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(tex, target, 0, 0, 0, 0, 0, width, height);
			
			return true;
		}
		
		public void readTarget() {
			this.data = null;
			
			if(target == null) return;
			
			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "export-" + window.framebuffer.getName(), GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, width * height * 4);
			RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(target, buffer, 0l, () -> {
				try(GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
					this.data = new byte[view.data().remaining()];
					view.data().get(this.data);
				}
				buffer.close();
			}, 0);
		}
		
		public void destroy() {
			target.close();
		}
		
	}
	
}
