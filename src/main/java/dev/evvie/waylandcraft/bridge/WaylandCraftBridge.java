package dev.evvie.waylandcraft.bridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeEGL;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.desktop.RawDesktopEntry;
import dev.evvie.waylandcraft.render.BufferTexture.DmabufTexture;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class WaylandCraftBridge {
	
	private long instance;
	private final Backend backend;
	private ArrayList<WLCToplevel> toplevels = new ArrayList<WLCToplevel>();
	private ArrayList<WLCPopup> popups = new ArrayList<WLCPopup>();
	private ArrayList<X11Window> x11Toplevels = new ArrayList<X11Window>();
	private ArrayList<WLCSurface> surfaces = new ArrayList<WLCSurface>();
	private ArrayList<DmabufTexture> dmabufs = new ArrayList<DmabufTexture>();
	private ArrayList<WindowFramebuffer> framebuffers = new ArrayList<WindowFramebuffer>();
	
	public IconSurface dndIcon = null;
	
	private LinkedList<WLCToplevel> focusOrder = new LinkedList<WLCToplevel>();
	private long lastX11DebugLogMs = 0;
	
	private ArrayList<WLCToplevel> newToplevels = new ArrayList<WLCToplevel>();
	
	private @Nullable Integer lastMoveRequestSerial = null;
	private @Nullable ResizeRequest lastResizeRequest = null;
	
	static {
		boolean loaded = false;
		InputStream inputStream = WaylandCraftBridge.class.getResourceAsStream("/libwaylandcraft.so");
		if(inputStream != null) {
			try {
				byte[] data = inputStream.readAllBytes();
				inputStream.close();
				
				File temp = File.createTempFile("waylandcraft-", "-libwaylandcraft.so");
				temp.deleteOnExit();
				
				FileOutputStream outputStream = new FileOutputStream(temp);
				outputStream.write(data);
				outputStream.close();
				
				System.load(temp.getAbsolutePath());
				loaded = true;
				
				WaylandCraft.LOGGER.info("Loaded native library from jar");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(!loaded) {
			WaylandCraft.LOGGER.info("Native library could not be loaded from jar. Attempting to load from system");
			System.loadLibrary("waylandcraft");
		}
	}
	
	private WaylandCraftBridge(long handle, Backend backend) {
		this.instance = handle;
		this.backend = backend;
	}

	private enum Backend {
		WAYLAND,
		X11;

		static Backend fromEnvironment() {
			String raw = System.getenv("WAYLANDCRAFT_BACKEND");
			if(raw == null || raw.isBlank()) return WAYLAND;

			return switch(raw.trim().toLowerCase()) {
				case "x11" -> X11;
				case "wayland" -> WAYLAND;
				default -> throw new IllegalArgumentException("Unknown WAYLANDCRAFT_BACKEND: " + raw);
			};
		}
	}
	
	public static WaylandCraftBridge start() {
		long eglDisplay = GLFWNativeEGL.glfwGetEGLDisplay();
		if(eglDisplay == 0) {
			throw new RuntimeException("Failed to get EGL display!");
		}
		
		long handle = init(GLFW.Functions.GetProcAddress, eglDisplay);
		return new WaylandCraftBridge(handle, Backend.WAYLAND);
	}

	public static WaylandCraftBridge startX11() {
		long handle = initX11();
		return new WaylandCraftBridge(handle, Backend.X11);
	}

	public static WaylandCraftBridge startSelected() {
		Backend backend = Backend.fromEnvironment();
		return backend == Backend.X11 ? startX11() : start();
	}
	
	protected WLCToplevel getOrCreateToplevel(long handle) {
		for(WLCToplevel toplevel : toplevels) {
			if(toplevel.getHandle() == handle) return toplevel;
		}
		WLCToplevel toplevel = new WLCToplevel(handle);
		
		long surfaceHandle = toplevelSurface(this.instance, handle);
		WLCSurface surface = getOrCreateSurface(surfaceHandle);
		toplevel.surface = surface;
		
		toplevels.add(toplevel);
		return toplevel;
	}
	
	public WLCToplevel[] getNewToplevels() {
		WLCToplevel[] toplevels = newToplevels.toArray(WLCToplevel[]::new);
		newToplevels.clear();
		
		return toplevels;
	}
	
	protected WLCPopup getOrCreatePopup(long handle) {
		for(WLCPopup popup : popups) {
			if(popup.getHandle() == handle) return popup;
		}
		WLCPopup popup = new WLCPopup(handle);
		
		long surfaceHandle = popupSurface(this.instance, handle);
		WLCSurface surface = getOrCreateSurface(surfaceHandle);
		popup.surface = surface;
		
		popup.parentHandle = popupParent(this.instance, handle);
		
		popups.add(popup);
		return popup;
	}
	
	protected WLCSurface getOrCreateSurface(long handle) {
		for(WLCSurface surface : surfaces) {
			if(surface.getHandle() == handle) return surface;
		}
		WLCSurface surface = new WLCSurface(handle);
		surfaces.add(surface);
		return surface;
	}
	
	protected DmabufTexture getDmabuf(long handle) {
		for(DmabufTexture dmabuf : dmabufs) {
			if(dmabuf.handle == handle) return dmabuf;
		}
		return null;
	}
	
	protected void addDmabuf(DmabufTexture dmabuf) {
		dmabufs.add(dmabuf);
	}
	
	private void deleteNonExistingToplevels(long[] remainingHandles) {
		ArrayList<WLCToplevel> toplevels_new = new ArrayList<WLCToplevel>();
		for(WLCToplevel toplevel : this.toplevels) {
			if(ArrayUtils.contains(remainingHandles, toplevel.getHandle())) {
				toplevels_new.add(toplevel);
			}
			else {
				freeToplevel(this.instance, toplevel.takeHandle());
			}
		}
		this.toplevels = toplevels_new;
	}
	
	private void deleteNonExistingPopups(long[] remainingHandles) {
		ArrayList<WLCPopup> popups_new = new ArrayList<WLCPopup>();
		for(WLCPopup popup : this.popups) {
			if(ArrayUtils.contains(remainingHandles, popup.getHandle())) {
				popups_new.add(popup);
			}
			else {
				freePopup(this.instance, popup.takeHandle());
			}
		}
		this.popups = popups_new;
	}
	
	private void deleteNonExistingDmabufs(long[] remainingHandles) {
		ArrayList<DmabufTexture> dmabufs_new = new ArrayList<DmabufTexture>();
		for(DmabufTexture dmabuf : this.dmabufs) {
			if(ArrayUtils.contains(remainingHandles, dmabuf.handle)) {
				dmabufs_new.add(dmabuf);
			}
			else {
				dmabuf.free();
			}
		}
		this.dmabufs = dmabufs_new;
	}
	
	private void deleteUnvisitedSurfaces() {
		ArrayList<WLCSurface> surfaces_new = new ArrayList<WLCSurface>();
		for(WLCSurface surface : this.surfaces) {
			if(surface.visited) {
				surfaces_new.add(surface);
			}
			else {
				freeSurface(this.instance, surface.takeHandle());
			}
		}
		this.surfaces = surfaces_new;
	}
	
	private void findPopupParent(WLCPopup popup) {
		// Popups cannot change their parent, so if one is found, it's the one
		if(popup.parent != null) return;
		
		for(WLCToplevel toplevel : toplevels) {
			if(toplevel.getHandle() == popup.parentHandle) {
				popup.parent = toplevel;
				return;
			}
		}
		
		for(WLCPopup popup2 : popups) {
			if(popup2.getHandle() == popup.parentHandle) {
				popup.parent = popup2;
				return;
			}
		}
	}
	
	public void update() {
		if(backend == Backend.X11) {
			updateX11(this.instance);
			updateFramebuffers();
			return;
		}

		ProfilerFiller profiler = Profiler.get();
		profiler.push("wayland");
		
		profiler.push("update");
		// Update wayland clients
		update(this.instance);
		profiler.pop();
		
		// Find all available toplevels and delete ones that no longer exist
		long[] toplevelHandles = toplevels(instance);
		deleteNonExistingToplevels(toplevelHandles);
		
		// Find all available popups and delete ones that no longer exist
		long[] popupHandles = popups(instance);
		deleteNonExistingPopups(popupHandles);
		
		long[] minimizeRequests = minimizeReq(instance);
		long[] maximizeRequests = maximizeReq(instance);
		long[] unmaximizeRequests = unmaximizeReq(instance);
		long[] fullscreenRequests = fullscreenReq(instance);
		long[] unfullscreenRequests = unfullscreenReq(instance);
		long[] fullscreened = fullscreened(instance);
		
		int[] moveRequest = moveRequest(instance);
		if(moveRequest != null) {
			lastMoveRequestSerial = moveRequest[0];
		}
		
		int[] resizeRequest = resizeRequest(instance);
		if(resizeRequest != null) {
			lastResizeRequest = new ResizeRequest(resizeRequest[0], resizeRequest[1]);
		}
		
		// Reset surface visited state
		for(WLCSurface surface : surfaces) {
			surface.visited = false;
		}
		
		profiler.push("surfaces");
		// Create new toplevels when necessary
		// Update surface tree geometry and properties of all toplevels
		for(long handle : toplevelHandles) {
			WLCToplevel toplevel = getOrCreateToplevel(handle);
			WLCSurface root = toplevel.getSurfaceTree();
			toplevel.lastChild = updateSurfaceTree(root);
			
			updateGeometry(toplevel);
			toplevel.title = toplevelTitle(toplevel.getHandle());
			toplevel.appID = toplevelAppID(toplevel.getHandle());
			
			if(ArrayUtils.contains(minimizeRequests, handle)) toplevel.requests.minimize = true;
			if(ArrayUtils.contains(maximizeRequests, handle)) toplevel.requests.maximize= true;
			if(ArrayUtils.contains(unmaximizeRequests, handle)) toplevel.requests.unmaximize = true;
			if(ArrayUtils.contains(fullscreenRequests, handle)) toplevel.requests.fullscreen = true;
			if(ArrayUtils.contains(unfullscreenRequests, handle)) toplevel.requests.unfullscreen = true;
			
			toplevel.fullscreen = ArrayUtils.contains(fullscreened, handle);
		}
		
		// Create new popups when necessary
		// Update surface tree geometry, parent relationships and offsets of all popups
		for(long handle : popupHandles) {
			WLCPopup popup = getOrCreatePopup(handle);
			findPopupParent(popup);
			
			int[] offset = popupOffset(handle);
			popup.offsetX = offset[0];
			popup.offsetY = offset[1];
			
			WLCSurface root = popup.getSurfaceTree();
			popup.lastChild = updateSurfaceTree(root);
			updateGeometry(popup);
		}
		
		long dndIconHandle = dndIcon(instance);
		if(dndIconHandle != 0) {
			WLCSurface dndIconSurface = getOrCreateSurface(dndIconHandle);
			if(dndIcon != null && dndIcon.surface != dndIconSurface) dndIcon = null;
			if(dndIcon == null) dndIcon = new IconSurface(dndIconSurface);
			
			updateSurfaceData(instance, dndIcon.surface);
			dndIcon.surface.visited = true;
		}
		else {
			dndIcon = null;
		}
		
		// All surface trees have now been walked. Now delete all unvisited surfaces
		deleteUnvisitedSurfaces();
		profiler.pop();
		
		// Resolve surface parent handles to actual surfaces
		for(WLCSurface surface : surfaces) {
			if(surface.parentHandle != 0) {
				surface.parent = getOrCreateSurface(surface.parentHandle);
			}
			else {
				surface.parent = null;
			}
		}
		
		List<WLCAbstractWindow> allWindows = Stream.of(toplevels, popups).flatMap((l) -> l.stream()).collect(Collectors.toList());
		
		// Update all surface buffers
		for(WLCAbstractWindow window : allWindows) {
			WLCSurface root = window.getSurfaceTree();
			for(WLCSurface surface = root; surface != null; surface = surface.getNextChild()) {
				updateSurfaceData(instance, surface);
				calculateSubpos(surface);
			}
		}
		
		for(WLCToplevel toplevel : toplevels) {
			boolean mapped = toplevel.isMapped();
			if(mapped && !toplevel.wasMapped) {
				newToplevels.add(toplevel);
			}
			toplevel.wasMapped = mapped;
		}
		
		profiler.push("framebuffer");
		updateFramebuffers();
		profiler.pop();
		
		deleteNonExistingDmabufs(dmabufs(instance));
		
		updateFocusOrder();
		
		// Do client frame callbacks
		for(WLCSurface surface : surfaces) {
			sendFrame(surface.getHandle());
		}
		
		profiler.pop();
	}
	
	private void updateFramebuffers() {
		List<WLCAbstractWindow> allWindows = Stream.of(toplevels, popups).flatMap((l) -> l.stream()).collect(Collectors.toList());
		if(backend == Backend.X11) {
			allWindows.addAll(x11Toplevels);
		}
		
		// Render windows
		for(WLCAbstractWindow window : allWindows) {
			if(window.framebuffer == null) {
				window.framebuffer = new WindowFramebuffer(window.getSurfaceTree());
				framebuffers.add(window.framebuffer);
			}
			window.framebuffer.render();
		}
		
		// Render dnd icon
		if(dndIcon != null) {
			if(dndIcon.framebuffer == null) {
				dndIcon.framebuffer = new WindowFramebuffer(dndIcon.surface);
				framebuffers.add(dndIcon.framebuffer);
			}
			dndIcon.framebuffer.render();
		}
		
		// Cleanup unused framebuffers
		ArrayList<WindowFramebuffer> usedFramebuffers = new ArrayList<WindowFramebuffer>();
		for(WindowFramebuffer framebuffer : framebuffers) {
			if(framebuffer.surfaceTree.isAlive()) {
				usedFramebuffers.add(framebuffer);
			}
			else {
				framebuffer.destroy();
			}
		}
		framebuffers.retainAll(usedFramebuffers);
		
		WindowFramebuffer.endFrame();
	}
	
	private void updateGeometry(WLCAbstractWindow window) {
		int[] data = surfaceXDGGeometry(window.surface.getHandle());
		SurfaceGeometry geometry;
		
		if(data == null) {
			geometry = new SurfaceGeometry(0, 0, window.surface.width(), window.surface.height());
		}
		else {
			geometry = new SurfaceGeometry(data[0], data[1], data[2], data[3]);
		}
		
		window.geometry = geometry;
	}
	
	private void calculateSubpos(WLCSurface surface) {
		if(surface.parent != null) {
			calculateSubpos(surface.parent);
			surface.xSubpos = surface.parent.xSubpos + surface.xoff;
			surface.ySubpos = surface.parent.ySubpos + surface.yoff;
		}
		else {
			surface.xSubpos = 0;
			surface.ySubpos = 0;
		}
	}
	
	public WLCToplevel[] getToplevels() {
		if(backend == Backend.X11) return new WLCToplevel[0];
		return toplevels.toArray(new WLCToplevel[toplevels.size()]);
	}
	
	public WLCToplevel[] getMappedToplevels() {
		if(backend == Backend.X11) return new WLCToplevel[0];
		return toplevels.stream().filter((t) -> t.isMapped()).toArray(WLCToplevel[]::new);
	}
	
	public WLCToplevel getToplevel(long handle) {
		if(backend == Backend.X11) return null;
		return toplevels.stream().filter((w) -> w.getHandle() == handle).findAny().orElse(null);
	}
	
	public WLCPopup[] getPopups() {
		if(backend == Backend.X11) return new WLCPopup[0];
		return popups.toArray(new WLCPopup[popups.size()]);
	}
	
	public WLCPopup[] getMappedPopups() {
		if(backend == Backend.X11) return new WLCPopup[0];
		return popups.stream().filter((t) -> t.isMapped()).toArray(WLCPopup[]::new);
	}
	
	public String getSocket() {
		if(backend == Backend.X11) {
			return socketX11(this.instance);
		}
		return socket(this.instance);
	}

	public boolean isX11Backend() {
		return backend == Backend.X11;
	}

	public long[] getX11Windows() {
		if(backend != Backend.X11) return new long[0];
		return x11Windows(this.instance);
	}

	public @Nullable String getX11WindowTitle(long handle) {
		if(backend != Backend.X11) return null;
		return x11WindowTitle(this.instance, handle);
	}

	public @Nullable String getX11WindowAppID(long handle) {
		if(backend != Backend.X11) return null;
		return x11WindowAppID(this.instance, handle);
	}

	public int[] getX11WindowGeometry(long handle) {
		if(backend != Backend.X11) return null;
		return x11WindowGeometry(this.instance, handle);
	}

	public boolean isX11WindowMapped(long handle) {
		if(backend != Backend.X11) return false;
		return x11WindowMapped(this.instance, handle);
	}

	private X11Window getOrCreateX11Window(long handle) {
		for(X11Window window : x11Toplevels) {
			if(window.getHandle() == handle) return window;
		}

		X11Window window = new X11Window(handle);
		x11Toplevels.add(window);
		return window;
	}

	public X11Window[] syncX11Windows() {
		if(backend != Backend.X11) return new X11Window[0];

		long[] handles = getX11Windows();
		int capturedCount = 0;
		x11Toplevels.removeIf((window) -> {
			boolean keep = ArrayUtils.contains(handles, window.getHandle());
			if(!keep) window.takeHandle();
			return !keep;
		});

		for(long handle : handles) {
			X11Window window = getOrCreateX11Window(handle);

			window.title = getX11WindowTitle(handle);
			window.appID = getX11WindowAppID(handle);

			int[] geom = getX11WindowGeometry(handle);
			if(geom != null && geom.length >= 4) {
				window.updateGeometry(geom[0], geom[1], geom[2], geom[3]);
			}

			long[] capture = x11WindowCapture(this.instance, handle);
			if(capture != null && capture.length >= 5) {
				capturedCount++;
				window.getSurfaceTree().attachShmBuffer(
					capture[0],
					(int) capture[2],
					(int) capture[3],
					0,
					(int) capture[4]
				);
			}
			else if(!isX11WindowMapped(handle)) {
				window.getSurfaceTree().removeBuffer();
			}
		}

		long now = System.currentTimeMillis();
		if(now - lastX11DebugLogMs >= 2000) {
			String names = x11Toplevels.stream()
				.map((w) -> w.title != null ? w.title : "<untitled>")
				.limit(4)
				.collect(Collectors.joining(", "));
			WaylandCraft.LOGGER.info("X11 sync: discovered=" + handles.length + ", captured=" + capturedCount + ", windows=[" + names + "]");
			lastX11DebugLogMs = now;
		}

		return x11Toplevels.stream().filter(X11Window::isMapped).toArray(X11Window[]::new);
	}
	
	public boolean inputRegionContains(WLCSurface surface, double x, double y) {
		if(backend == Backend.X11) return false;
		return checkInputRegion(surface.getHandle(), x, y);
	}
	
	public void sendMotion(double x, double y) {
		if(backend == Backend.X11) return;
		pointerMotion(instance, x, y);
	}
	
	public void sendMotionRefocus(WLCSurface surface, double x, double y) {
		if(backend == Backend.X11) return;
		pointerMotionFocus(instance, surface.getHandle(), x, y);
	}
	
	public void sendRelativeMotion(double dx, double dy) {
		if(backend == Backend.X11) return;
		pointerRelMotion(instance, dx, dy);
	}
	
	public void sendMotionOutside() {
		if(backend == Backend.X11) return;
		pointerLeave(instance);
	}
	
	public boolean maybeLockPointer(WLCSurface surface) {
		if(backend == Backend.X11) return false;
		return maybePointerLock(instance, surface.getHandle());
	}
	
	public void unlockPointer() {
		if(backend == Backend.X11) return;
		pointerUnlock(instance);
	}
	
	public int sendButton(int button, int state) {
		if(backend == Backend.X11) return 0;
		return pointerButton(instance, button, state);
	}
	
	public void sendScroll(int axis, double value) {
		if(backend == Backend.X11) return;
		pointerAxis(instance, axis, value);
	}
	
	public CursorShape getCursorShape() {
		if(backend == Backend.X11) return null;
		return CursorShape.fromId(cursorShape(instance));
	}
	
	public void focusSurface(@Nullable WLCToplevel toplevel) {
		if(backend == Backend.X11) return;
		long handle = 0;
		if(toplevel != null) {
			handle = toplevel.getHandle();
		}
		
		keyboardFocus(instance, handle);
		
		// Make toplevel most recently focused
		if(toplevel != null) {
			focusOrder.remove(toplevel);
			focusOrder.addLast(toplevel);
		}
	}
	
	public void activateKeyboard() {
		if(backend == Backend.X11) return;
		keyboardActivate(instance);
	}
	
	public void deactivateKeyboard() {
		if(backend == Backend.X11) return;
		keyboardDeactivate(instance);
	}
	
	private void updateFocusOrder() {
		focusOrder.removeIf((t) -> !toplevels.contains(t));
		for(WLCToplevel toplevel : toplevels) {
			if(!focusOrder.contains(toplevel)) focusOrder.addLast(toplevel);
		}
	}
	
	// Find the most recently focused toplevel that exists
	public WLCToplevel getMostRecentFocus() {
		if(backend == Backend.X11) return null;
		updateFocusOrder();
		return focusOrder.peekLast();
	}
	
	// Find the most recently focused toplevel that exists
	public Stream<WLCToplevel> getMostToLeastRecentFocus() {
		if(backend == Backend.X11) return Stream.empty();
		updateFocusOrder();
		return focusOrder.reversed().stream();
	}
	
	public void pressKey(int scancode) {
		if(backend == Backend.X11) return;
		keyboardInput(instance, scancode, 1);
	}
	
	public void releaseKey(int scancode) {
		if(backend == Backend.X11) return;
		keyboardInput(instance, scancode, 0);
	}
	
	public void internalKeyUpdate(int scancode, boolean pressed) {
		if(backend == Backend.X11) return;
		keyboardUpdate(instance, scancode, pressed);
	}
	
	public void resizeToplevelInteractive(WLCToplevel toplevel, int width, int height) {
		toplevelResize(toplevel.getHandle(), width, height, true);
	}
	
	public void resizeToplevel(WLCToplevel toplevel, int width, int height) {
		toplevelResize(toplevel.getHandle(), width, height, false);
	}
	
	public void resizeToplevelOverride(WLCToplevel toplevel, int width, int height) {
		toplevelResizeOvr(toplevel.getHandle(), width, height);
	}
	
	public void maximizeToplevel(WLCToplevel toplevel) {
		toplevelMaximize(instance, toplevel.getHandle());
	}
	
	public void fullscreenToplevel(WLCToplevel toplevel) {
		toplevelFullscreen(instance, toplevel.getHandle());
	}
	
	public Integer checkMoveRequest() {
		if(lastMoveRequestSerial == null) return null;
		int serial = lastMoveRequestSerial.intValue();
		lastMoveRequestSerial = null;
		return serial;
	}
	
	public ResizeRequest checkResizeRequest() {
		if(lastResizeRequest == null) return null;
		ResizeRequest req = lastResizeRequest;
		lastResizeRequest = null;
		return req;
	}
	
	public void resizeOutput(int width, int height) {
		if(backend == Backend.X11) return;
		outputResize(instance, width, height);
	}
	
	public void setOutputBounds(int width, int height) {
		if(backend == Backend.X11) return;
		outputSetBounds(instance, width, height);
	}
	
	public Size getOutputSize() {
		if(backend == Backend.X11) {
			int width = net.minecraft.client.Minecraft.getInstance().getWindow().getWidth();
			int height = net.minecraft.client.Minecraft.getInstance().getWindow().getHeight();
			return new Size(width, height);
		}
		int[] size = outputSize(instance);
		return new Size(size[0], size[1]);
	}
	
	public Size getOutputBounds() {
		if(backend == Backend.X11) {
			int width = net.minecraft.client.Minecraft.getInstance().getWindow().getWidth();
			int height = net.minecraft.client.Minecraft.getInstance().getWindow().getHeight();
			return new Size(width, height);
		}
		int[] size = outputBounds(instance);
		return new Size(size[0], size[1]);
	}
	
	public RawDesktopEntry loadDesktopEntry(File path) {
		if(backend == Backend.X11) return loadDesktopEntryX11(instance, path.getAbsolutePath());
		return loadDesktopEntry(instance, path.getAbsolutePath());
	}

	public RawDesktopEntry[] loadSystemDesktopEntries() {
		if(backend == Backend.X11) return loadDesktopEntriesX11(instance);
		return loadDesktopEntries(instance);
	}
	
	public boolean renderSVG(File file, int width, int height, long ptr) {
		return renderSVG(file.getAbsolutePath(), width, height, ptr);
	}
	
	public boolean execApp(String appId) {
		if(backend == Backend.X11) return execAppX11(instance, appId);
		return execApp(instance, appId);
	}
	
	public void setKeymapDefault() {
		if(backend == Backend.X11) return;
		setKeymapDefault(instance);
	}
	
	public String exportKeymap() {
		if(backend == Backend.X11) return "";
		return exportKeymap(instance);
	}
	
	public boolean setKeymapFromStr(String keymap) {
		if(backend == Backend.X11) return true;
		return setKeymapFromStr(instance, keymap);
	}
	
	public Integer checkDndRequest() {
		int[] serial = checkDndRequest(instance);
		if(serial == null) return null;
		return serial[0];
	}
	
	public void dndCancel() {
		dndCancel(instance);
	}
	
	public void dndDrop() {
		dndDrop(instance);
	}
	
	public void sendDndMotion(WLCSurface surface, double x, double y) {
		long handle = surface == null ? 0 : surface.getHandle();
		dndMotion(instance, handle, x, y);
	}
	
	public static record Size(int width, int height) {}
	
	public static record ResizeRequest(int serial, int edges) {}
	
	private static native long init(long glfwGetProcAddress, long eglDisplay);
	private static native long initX11();
	private static native void update(long instance);
	private static native void updateX11(long instance);
	private static native String socket(long instance);
	private static native String socketX11(long instance);
	private static native long[] x11Windows(long instance);
	private static native String x11WindowTitle(long instance, long handle);
	private static native String x11WindowAppID(long instance, long handle);
	private static native int[] x11WindowGeometry(long instance, long handle);
	private static native boolean x11WindowMapped(long instance, long handle);
	private static native long[] x11WindowCapture(long instance, long handle);
	private static native void sendFrame(long handle);
	
	private static native void updateSurfaceData(long instance, WLCSurface surface);
	
	private static native long[] toplevels(long instance);
	private static native long toplevelSurface(long instance, long handle);
	private static native String toplevelTitle(long handle);
	private static native String toplevelAppID(long handle);
	// Resize toplevel
	private static native void toplevelResize(long handle, int width, int height, boolean interactive);
	// Resize toplevel override, keep maximized and fullscreen state, stop interactive resize
	private static native void toplevelResizeOvr(long handle, int width, int height);
	
	// Collect all toplevels that have sent a minimize request and clear the list
	private static native long[] minimizeReq(long instance);
	// Collect all toplevels that have sent a maximize request and clear the list
	private static native long[] maximizeReq(long instance);
	// Collect all toplevels that have sent an unmaximize request and clear the list
	private static native long[] unmaximizeReq(long instance);
	// Collect all toplevels that have sent a fullscreen request and clear the list
	private static native long[] fullscreenReq(long instance);
	// Collect all toplevels that have sent an unfullscreen request and clear the list
	private static native long[] unfullscreenReq(long instance);
	
	// Collect up to one serial of a sent interactive move request
	private static native int[] moveRequest(long instance);
	// Collect up to one serial of a sent interactive resize request
	private static native int[] resizeRequest(long instance);
	
	// All toplevels that are currently in fullscreen
	private static native long[] fullscreened(long instance);
	
	private static native void toplevelMaximize(long instance, long handle);
	private static native void toplevelFullscreen(long instance, long handle);
	
	private static native long[] popups(long instance);
	private static native long popupSurface(long instance, long handle);
	// Query the parent of a popup
	// Returned handle is a handle either to a toplevel or another popup
	private static native long popupParent(long instance, long handle);
	// Query popup local offset coordinates
	// Returns two-element list containing x,y
	private static native int[] popupOffset(long handle);
	
	// Query the xdg_surface window geometry of a toplevel or popup.
	// handle should be the handle to the root WLCSurface
	// Returns four-element array containing x,y,width,height which could be null
	private static native int[] surfaceXDGGeometry(long handle);
	
	private static native long[] dmabufs(long instance);
	
	// Updates the surface tree given by the root surface
	// This changes the doubly linked list of the WLCSurfaces.
	// The returned surface is the last (most deeply nested) child
	private native WLCSurface updateSurfaceTree(WLCSurface root);
	
	// Check if point in surface input region
	private static native boolean checkInputRegion(long surfaceHandle, double x, double y);
	
	// Create pointer motion event
	private static native void pointerMotion(long instance, double x, double y);
	
	// Create pointer motion event
	private static native void pointerMotionFocus(long instance, long handle, double x, double y);
	
	// Send relative pointer motion to surface with pointer focus
	private static native void pointerRelMotion(long instance, double dx, double dy);
	
	private static native boolean maybePointerLock(long instance, long handle);
	
	private static native void pointerUnlock(long instance);
	
	// Remove pointer focus from all surfaces
	private static native void pointerLeave(long instance);
	
	// Create pointer button event. `button` has to be the linux button code, state is 1 for pressed, 0 for released
	private static native int pointerButton(long instance, int button, int state);
	
	// Create pointer axis event. `axis` is the scroll axis (0 for vertical, 1 for horizontal)
	private static native void pointerAxis(long instance, int axis, double value);
	
	// Get active cursor image
	private static native int cursorShape(long instance);
	
	// Set keyboard focus to a wayland surface. The handle may be 0 to unfocus any surfaces
	private static native void keyboardFocus(long instance, long surfaceHandle);
	
	private static native void keyboardActivate(long instance);
	private static native void keyboardDeactivate(long instance);
	
	// Keyboard input. scancode is the raw keycode. action: 0 is released, 1 is pressed.
	private static native void keyboardInput(long instance, int scancode, int action);
	
	// Update internal key state
	private static native void keyboardUpdate(long instance, int scancode, boolean pressed);
	
	private static native int[] outputSize(long instance);
	private static native int[] outputBounds(long instance);
	
	// Update virtual output dimensions
	private static native void outputResize(long instance, int width, int height);
	
	// Update virtual output maximum window bounds
	private static native void outputSetBounds(long instance, int width, int height);
	
	private static native void freeSurface(long instance, long handle);
	private static native void freeToplevel(long instance, long handle);
	private static native void freePopup(long instance, long handle);
	
	private static native RawDesktopEntry loadDesktopEntry(long instance, String path);
	private static native RawDesktopEntry[] loadDesktopEntries(long instance);
	private static native RawDesktopEntry loadDesktopEntryX11(long instance, String path);
	private static native RawDesktopEntry[] loadDesktopEntriesX11(long instance);

	private static native boolean renderSVG(String path, int width, int height, long ptr);

	private static native boolean execApp(long instance, String appId);
	private static native boolean execAppX11(long instance, String appId);
	
	private static native void setKeymapDefault(long instance);
	private static native String exportKeymap(long instance);
	private static native boolean setKeymapFromStr(long instance, String keymap);
	
	private static native int[] checkDndRequest(long instance);
	private static native boolean checkDndActive(long instance);
	private static native void dndCancel(long instance);
	private static native void dndDrop(long instance);
	private static native void dndMotion(long instance, long surface, double x, double y);
	private static native long dndIcon(long instance);
	
}
