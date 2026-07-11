package dev.evvie.waylandcraft.datasync;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.network.WindowDataPayload;
import dev.evvie.waylandcraft.network.WindowMetadataPayload;
import dev.evvie.waylandcraft.render.WindowFramebuffer.FramebufferDamage;
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
		
		for(WindowExportState export : exports) {
			ImagePatch patch;
			while((patch = export.patches.pollLast()) != null) {
				patch = optimizeOpaquePatch(patch);
				ClientPlayNetworking.send(new WindowDataPayload(export.handle, patch));
			}
		}
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
		if(!export.updateMetadata()) return;
		
		if(export.metadataDirty) {
			// Create full-sized patch if metadata dirty
			System.out.println("Creating full-sized patch");
			export.createPatch(0, 0, export.fbWidth, export.fbHeight);
			window.framebuffer.collectDamage(); // Clear damage here because metadata is dirty
			return;
		}
		
		// Create patches for damage updates
		List<FramebufferDamage> damage = window.framebuffer.collectDamage();
		for(FramebufferDamage d : damage) {
			System.out.println("Creating patch (x=" + d.x() + ", y=" + d.y() + ", width=" + d.width() + ", height=" + d.height() + ")");
			export.createPatch(d.x(), d.y(), d.width(), d.height());
		}
	}
	
	// Attempt to convert a FORMAT_RAW_RGBA patch into a FORMAT_RAW_RGB patch if it is fully opaque
	private ImagePatch optimizeOpaquePatch(ImagePatch patch) {
		if(patch.format() != ImagePatch.FORMAT_RAW_RGBA) return patch;
		
		byte[] data = patch.data();
		short opaque = 0xff;
		for(int i = 0; i < data.length / 4; i++) {
			opaque &= data[i * 4 + 3];
		}
		if(opaque != 0xff) return patch;
		
		byte[] ndata = new byte[patch.width() * patch.height() * 3];
		for(int i = 0; i < data.length / 4; i++) {
			ndata[i * 3 + 0] = data[i * 4 + 0];
			ndata[i * 3 + 1] = data[i * 4 + 1];
			ndata[i * 3 + 2] = data[i * 4 + 2];
		}
		
		return new ImagePatch(ImagePatch.FORMAT_RAW_RGB, patch.x(), patch.y(), patch.width(), patch.height(), ndata);
	}
	
	public void reset() {
		reset = true;
	}
	
	public static class PatchTexture {
		
		private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
		
		private String name;
		private GpuTexture texture;
		private int width = -1;
		private int height = -1;
		
		public PatchTexture(String name, int width, int height) {
			this.name = name;
			setSize(width, height);
		}
		
		public void setSize(int width, int height) {
			if(width < 1 || height < 1) throw new IllegalStateException("width and height have to be positive");
			if(width == this.width && height == this.height) return;
			
			this.destroy();
			
			this.width = width;
			this.height = height;
			this.texture = RenderSystem.getDevice().createTexture(name, TEXTURE_USAGE, TextureFormat.RGBA8, width, height, 1, 1);
		}
		
		public GpuTexture getTexture() {
			return texture;
		}
		
		public int width() {
			return width;
		}
		
		public int height() {
			return height;
		}
		
		public void destroy() {
			if(texture == null) return;
			
			texture.close();
			texture = null;
		}
		
	}
	
	public static class WindowExportState {
		
		public final WLCAbstractWindow window;
		public final WindowHandle handle;
		
		public PatchTexture target = null;
		
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
		
		public void createPatch(int x, int y, int width, int height) {
			GpuTexture tex = window.framebuffer.getTexture();
			if(tex == null) return;
			if(width > MAX_SIZE || height > MAX_SIZE) return;
			
			if(target == null) target = new PatchTexture("export-" + window.framebuffer.getName(), width, height);
			else target.setSize(width, height);
			
			// Copy framebuffer region to patch
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(tex, target.getTexture(), 0, 0, 0, x, y, width, height);
			
			// Read patch to RGBA bytes
			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "export-" + window.framebuffer.getName(), GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, target.width() * target.height() * 4);
			RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(target.getTexture(), buffer, 0l, () -> {
				try(GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
					byte[] data = new byte[view.data().remaining()];
					view.data().get(data);
					
					ImagePatch patch = new ImagePatch(ImagePatch.FORMAT_RAW_RGBA, x, y, width, height, data);
					this.patches.add(patch);
				}
				buffer.close();
			}, 0);
		}
		
		public void destroy() {
			if(target != null) target.destroy();
		}
		
	}
	
}
