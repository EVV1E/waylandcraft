package dev.evvie.waylandcraft.bridge;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.render.BufferTexture;
import dev.evvie.waylandcraft.render.BufferTexture.DmabufTexture;
import dev.evvie.waylandcraft.render.BufferTexture.ShmBufferTexture;
import dev.evvie.waylandcraft.render.BufferTexture.SinglePixelBufferTexture;
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
	
	// Attach a shared memory buffer
	// The surface width and height are reset to the given buffer dimensions.
	protected void attachShmBuffer(long ptr, int width, int height, int format, int stride) {
		if(this.buffer != null) {
			this.buffer.release();
		}
		this.buffer = new ShmBufferTexture(ptr, width, height, format, stride);
		this.width = width;
		this.height = height;
	}
	
	// Attach a single pixel buffer
	// The surface width and height are reset to 1.
	protected void attachSinglePixelBuffer(byte r, byte g, byte b, byte a) {
		if(this.buffer != null) {
			this.buffer.release();
		}
		this.buffer = new SinglePixelBufferTexture(r, g, b, a);
		this.width = 1;
		this.height = 1;
	}
	
	// Attach an already known dmabuf
	// The surface width and height are reset to the given buffer dimensions.
	// Returns false if no DmabufTexture by that handle was found.
	protected boolean attachDmabuf(long handle) {
		if(this.buffer != null) {
			this.buffer.release();
		}
		
		this.buffer = WaylandCraft.instance.bridge.getDmabuf(handle);
		if(this.buffer != null) {
			this.width = buffer.width;
			this.height = buffer.height;
			
			DmabufTexture dmabuf = (DmabufTexture) this.buffer;
			dmabuf.copyData();
		}
		return this.buffer != null;
	}
	
	// Create and attach a new DmabufTexture
	// MUST only be used when attachDmabuf returns false for this handle!
	protected void attachNewDmabuf(long handle, long eglImage, int width, int height) {
		DmabufTexture dmabuf = new DmabufTexture(handle, eglImage, width, height);
		WaylandCraft.instance.bridge.addDmabuf(dmabuf);
		
		if(!attachDmabuf(handle)) {
			throw new RuntimeException("Failed to attach newly created dmabuf");
		}
	}
	
	protected void removeBuffer() {
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
