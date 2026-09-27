package dev.evvie.waylandcraft.bridge;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.render.BufferTexture;
import dev.evvie.waylandcraft.render.BufferTexture.DmabufTexture;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import net.minecraft.util.Mth;

public class WLCSurface {
	
	protected long handle = 0;
	
	@Nullable
	private BufferTexture buffer = null;
	
	@Nullable
	protected WLCSurface parent = null;
	
	protected WLCSurface[] children = new WLCSurface[0];
	
	// Entire surface tree in drawing order (back to front). Contains the surface itself.
	// Only updated for root surfaces
	@Nullable
	protected WLCSurface[] surfaceDrawTree = new WLCSurface[] {this};
	
	// Entire surface tree in input order (front to back). Contains the surface itself.
	// Only updated for root surfaces
	@Nullable
	protected WLCSurface[] surfaceInputTree = new WLCSurface[] {this};
	
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
	
	private ArrayList<SurfaceDamage> surfaceDamage = new ArrayList<>();
	private ArrayList<BufferDamage> bufferDamage = new ArrayList<>();
	
	@Nullable
	private WindowFramebuffer framebuffer = null;
	
	private WLCSurface(long handle) {
		this.handle = handle;
		System.out.println("new surface: " + this);
	}
	
	public boolean isAlive() {
		return handle != 0;
	}
	
	protected void destroy() {
		removeBuffer();
		destroyFramebuffer();
		System.out.println("destroy: " + this);
	}
	
	private void destroyFramebuffer() {
		if(framebuffer != null) framebuffer.destroy();
		framebuffer = null;
	}
	
	protected void commit() {
		if(parent != null) {
			destroyFramebuffer();
			return;
		}
		if(framebuffer == null) framebuffer = new WindowFramebuffer(this);
		framebuffer.render();
//		System.out.println("commit " + this);
	}
	
	public WindowFramebuffer getFramebuffer() {
		return framebuffer;
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
	
	// Attach a dmabuf
	// The surface width and height are reset to the given buffer dimensions.
	protected void attachDmabuf(DmabufTexture dmabuf) {
		removeBuffer();
		
		this.buffer = dmabuf;
		this.width = buffer.width;
		this.height = buffer.height;
		
		dmabuf.copyData();
	}
	
	protected void removeBuffer() {
		BufferTexture prev = buffer;
		this.buffer = null;
		if(prev != null) {
			// Make sure this.buffer is nulled first before release because DmabufTexture#release searches
			// for surfaces that have it attached
			prev.release();
		}
		this.width = this.height = 0;
	}
	
	// Set viewport source dimensions
	// Crops the surface to the specified rectangle.
	protected void setViewportSrc(double x, double y, double width, double height) {
		this.sourceView = new ViewportSource(x, y, width, height);
		this.width = (int) width;
		this.height = (int) height;
	}
	
	protected void unsetViewportSrc() {
		this.sourceView = null;
	}
	
	// Set viewport destination dimensions
	// Overrides this surfaces width & height values.
	protected void setViewportDst(int width, int height) {
		this.width = width;
		this.height = height;
	}
	
	protected void clearDamage() {
		surfaceDamage.clear();
		bufferDamage.clear();
	}
	
	protected void addSurfaceDamage(int x, int y, int width, int height) {
		if(buffer == null) return;
		
		this.surfaceDamage.add(new SurfaceDamage(x, y, width, height));
		
		double sourceX = 0;
		double sourceY = 0;
		double sourceWidth = buffer.width;
		double sourceHeight = buffer.height;
		if(sourceView != null) {
			sourceX = sourceView.x;
			sourceY = sourceView.y;
			sourceWidth = sourceView.width;
			sourceHeight = sourceView.height;
		}
		
		double bx = sourceX + x / (double) this.width * sourceWidth;
		double by = sourceY + y / (double) this.height * sourceHeight;
		double bw = width / (double) this.width * sourceWidth;
		double bh = height / (double) this.height * sourceHeight;
		
		this.bufferDamage.add(new BufferDamage(Mth.floor(bx), Mth.floor(by), Mth.ceil(bw), Mth.ceil(bh)));
	}
	
	protected void addBufferDamage(int x, int y, int width, int height) {
		if(buffer == null) return;
		
		bufferDamage.add(new BufferDamage(x, y, width, height));
		
		double sx = x;
		double sy = y;
		double sw = width;
		double sh = height;
		
		double sourceWidth = buffer.width;
		double sourceHeight = buffer.height;
		if(sourceView != null) {
			sx -= sourceView.x;
			sy -= sourceView.y;
			sourceWidth = sourceView.width;
			sourceHeight = sourceView.height;
		}
		
		sx *= this.width / sourceWidth;
		sy *= this.height / sourceHeight;
		sw *= this.width / sourceWidth;
		sh *= this.height / sourceHeight;
		
		this.surfaceDamage.add(new SurfaceDamage(Mth.floor(sx), Mth.floor(sy), Mth.ceil(sw), Mth.ceil(sh)));
	}
	
	protected void calculateSubpos() {
		if(parent == null) {
			this.xSubpos = 0;
			this.ySubpos = 0;
		}
		else {
			this.xSubpos = parent.xSubpos + this.xoff;
			this.ySubpos = parent.ySubpos + this.yoff;
		}
		for(WLCSurface child : children) {
			child.calculateSubpos();
		}
	}
	
	public List<SurfaceDamage> getSurfaceDamage() {
		return surfaceDamage;
	}
	
	public List<BufferDamage> getBufferDamage() {
		return bufferDamage;
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
	
	public WLCSurface[] getChildren() {
		return this.children;
	}
	
	@Nullable
	public WLCSurface[] getDrawTree() {
		return surfaceDrawTree;
	}
	
	@Nullable
	public WLCSurface[] getInputTree() {
		return surfaceInputTree;
	}
	
	public native void sendFrame();
	public native boolean inputRegionContains(double x, double y);
	
	// Surface-local dimensions of the source rectangle in a buffer
	public static final record ViewportSource(double x, double y, double width, double height) {
	}
	
	// Surface-local region describing contents damage
	public static final record SurfaceDamage(int x, int y, int width, int height) {
	}
	
	// Buffer-local region describing contents damage
	public static final record BufferDamage(int x, int y, int width, int height) {
	}
	
}
