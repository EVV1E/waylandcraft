package dev.evvie.waylandcraft.bridge;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.render.BufferTexture;
import dev.evvie.waylandcraft.render.BufferTexture.DmabufTexture;
import net.minecraft.util.Mth;

public class WLCSurface {
	
	// Set to zero when this surface no longer exists
	private long handle;
	
	// Used by native code to tag used surfaces
	protected boolean visited;
	
	@Nullable
	private BufferTexture buffer = null;
	
	// Either a child of this surface or one of its siblings
	@Nullable
	protected WLCSurface nextChild = null;
	
	@Nullable
	protected WLCSurface prevChild = null;
	
	protected long parentHandle = 0;
	
	@Nullable
	protected WLCSurface parent = null;
	
	// Surface size. By default the size of the attached buffer.
	private int width = 0;
	private int height = 0;
	
	@Nullable
	private ViewportSource sourceView = null;
	
	// X and Y offsets relative to parent coords
	protected int xoff = 0;
	protected int yoff = 0;
	
	// Total calculated offsets
	public int xSubpos = 0;
	public int ySubpos = 0;
	
	private ArrayList<SurfaceDamage> damage = new ArrayList<>();
	
	protected WLCSurface(long handle) {
		this.handle = handle;
	}
	
	protected long getHandle() {
		return this.handle;
	}
	
	protected long takeHandle() {
		long old = this.handle;
		this.handle = 0;
		return old;
	}
	
	public boolean isAlive() {
		return handle != 0;
	}
	
	protected void destroy() {
		if(buffer != null) buffer.release();
	}
	
	// Attach a shared memory buffer
	// The surface width and height are reset to the given buffer dimensions.
	protected void attachShmBuffer(long ptr, int width, int height, int format, int stride) {
		removeBuffer();
		
		this.buffer = BufferTexture.createShmTexture(ptr, width, height, format, stride);
		this.width = width;
		this.height = height;
	}
	
	// Attach a single pixel buffer
	// The surface width and height are reset to 1.
	protected void attachSinglePixelBuffer(byte r, byte g, byte b, byte a) {
		removeBuffer();
		
		this.buffer = BufferTexture.createSinglePixelTexture(r, g, b, a);
		this.width = 1;
		this.height = 1;
	}
	
	// Attach an already known dmabuf
	// The surface width and height are reset to the given buffer dimensions.
	// Returns false if no DmabufTexture by that handle was found.
	protected boolean attachDmabuf(long handle, long releaseHandle) {
		removeBuffer();
		
		DmabufTexture dmabuf = WaylandCraft.instance.bridge.getDmabuf(handle);
		if(dmabuf == null) return false;
		
		this.buffer = dmabuf;
		this.width = buffer.width;
		this.height = buffer.height;
		
		dmabuf.copyData();
		
		RenderSystem.queueFencedTask(() -> {
			WaylandCraft.instance.bridge.sendBufferRelease(releaseHandle);
		});
		
		return true;
	}
	
	protected void removeBuffer() {
		if(buffer != null) buffer.release();
		this.buffer = null;
		this.width = this.height = 0;
	}
	
	// Set viewport source dimensions
	// Crops the surface to the specified rectangle.
	protected void setViewportSrc(double x, double y, double width, double height) {
		this.sourceView = new ViewportSource(x, y, width, height);
		this.width = (int) width;
		this.height = (int) height;
	}
	
	// Set viewport destination dimensions
	// Overrides this surfaces width & height values.
	protected void setViewportDst(int width, int height) {
		this.width = width;
		this.height = height;
	}
	
	protected void clearDamage() {
		damage.clear();
	}
	
	protected void addSurfaceDamage(int x, int y, int width, int height) {
		this.damage.add(new SurfaceDamage(x, y, width, height));
	}
	
	protected void addBufferDamage(int x, int y, int width, int height) {
		if(buffer == null) return;
		
		double sx = x;
		double sy = y;
		double sw = width;
		double sh = height;
		
		if(sourceView != null) {
			sx -= sourceView.x;
			sy -= sourceView.y;
		}
		
		sx *= this.width / buffer.width;
		sy *= this.height / buffer.height;
		sw *= this.width / buffer.width;
		sh *= this.height / buffer.height;
		
		addSurfaceDamage(Mth.floor(sx), Mth.floor(sy), Mth.ceil(sw), Mth.ceil(sh));
	}
	
	public List<SurfaceDamage> getDamage() {
		return damage;
	}
	
	public int width() {
		return width;
	}
	
	public int height() {
		return height;
	}
	
	public ViewportSource getViewportSource() {
		return sourceView;
	}
	
	@Nullable
	public BufferTexture getBuffer() {
		return this.buffer;
	}
	
	@Nullable
	public WLCSurface getParent() {
		return this.parent;
	}
	
	@Nullable
	public WLCSurface getNextChild() {
		return this.nextChild;
	}
	
	@Nullable
	public WLCSurface getPrevChild() {
		return this.prevChild;
	}
	
	// Surface-local dimensions of the source rectangle in a buffer
	public static final record ViewportSource(double x, double y, double width, double height) {
	}
	
	// Surface-local region describing contents damage
	public static final record SurfaceDamage(int x, int y, int width, int height) {
	}
	
}
