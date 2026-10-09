package dev.evvie.waylandcraft.bridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lwjgl.system.Platform;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufFeedbackData;
import dev.evvie.waylandcraft.bridge.dmabuf.DmabufFormat;
import dev.evvie.waylandcraft.desktop.RawDesktopEntry;
import dev.evvie.waylandcraft.egl.EGL;
import dev.evvie.waylandcraft.egl.EGLHelper;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import dev.evvie.waylandcraft.utils.CursorShape;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class WaylandCraftBridge {
	
	private long instance;
	private ArrayList<WLCToplevel> toplevels = new ArrayList<WLCToplevel>();
	private ArrayList<WLCPopup> popups = new ArrayList<WLCPopup>();
	private ArrayList<WLCSurface> surfaces = new ArrayList<WLCSurface>();
	
	private LinkedList<WLCToplevel> focusOrder = new LinkedList<WLCToplevel>();
	
	private ArrayList<WLCToplevel> newToplevels = new ArrayList<WLCToplevel>();
	
	private @Nullable Integer lastMoveRequestSerial = null;
	private @Nullable ResizeRequest lastResizeRequest = null;
	
	public WindowRequestHandlers requestHandlers = new WindowRequestHandlers();
	
	static {
		boolean loaded = false;
		InputStream inputStream = openNativeLibraryFromJar();
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
				
				WaylandCraftCommon.LOGGER.info("Loaded native library from jar");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(!loaded) {
			WaylandCraftCommon.LOGGER.info("Native library could not be loaded from jar. Attempting to load from system");
			System.loadLibrary("waylandcraft");
		}
	}
	
	private static InputStream loadResource(String path) {
		WaylandCraftCommon.LOGGER.info("Looking for '" + path + "'...");
		return WaylandCraftBridge.class.getResourceAsStream(path);
	}
	
	private static InputStream openNativeLibraryFromJar() {
		InputStream stream = null;
		
		/* Attempt to load manually built native library */
		stream = loadResource("/libwaylandcraft.so");
		if(stream != null) return stream;
		
		/* Attempt to load from release library path */
		String arch;
		switch(Platform.getArchitecture()) {
		case X64: arch = "x86_64"; break;
		case ARM64: arch = "arm64"; break;
		default: arch = null; break;
		}
		
		if(arch != null) {
			String platform = "linux-gnu-" + arch;
			stream = loadResource("/libwaylandcraft-" + platform + ".so");
			if(stream != null) return stream;
		}
		
		return null;
	}
	
	private WaylandCraftBridge(long instance) {
		/* Constructor used by the native code.
		 * DO NOT USE ANY BRIDGE FUNCTIONS HERE! The instance pointer is uninitialized until
		 * WaylandCraftBridge#init() returns!
		 */
		this.instance = instance;
	}
	
	public static WaylandCraftBridge start() {
		DmabufFeedbackData dmabufFeedbackData = initBackend();
		WaylandCraftBridge bridge = init(dmabufFeedbackData);
		
		// Add shutdown thread to clean up resources on normal exit
		Runtime.getRuntime().addShutdownHook(new Thread(bridge::shutdownHook));
		
		return bridge;
	}
	
	private static DmabufFeedbackData initBackend() {
		GpuDeviceBackend deviceBackend = RenderSystem.getDevice().backend;
		if(deviceBackend instanceof GlDevice) {
			return initBackendEGL();
		}
		
		WaylandCraftCommon.LOGGER.error("Unsupported graphics backend!");
		return null;
	}
	
	private static DmabufFeedbackData initBackendEGL() {
		long eglDisplay = EGL.getEGLDisplay();
		if(eglDisplay == 0) {
			throw new RuntimeException("Failed to get EGL display!");
		}
		
		String renderNodePath = EGLHelper.queryRenderNodePath(eglDisplay);
		if(renderNodePath == null) {
			WaylandCraftCommon.LOGGER.error("Failed to query for drm render node! This could indicate a software renderer. Disabling dmabuf functionality.");
			return null;
		}
		
		DmabufFormat[] formats = EGLHelper.queryDmabufFormats(eglDisplay).toArray(DmabufFormat[]::new);
		long device = drmDeviceByPath(renderNodePath);
		
		return new DmabufFeedbackData(device, formats);
	}
	
	private void shutdownHook() {
		shutdown(instance);
		instance = 0;
	}
	
	public void update() {
		ProfilerFiller profiler = Profiler.get();
		profiler.push("wayland");
		
		// Dispatch wayland client events
		profiler.push("dispatch clients");
		dispatchClients(instance);
		profiler.pop();
		
		// Add newly mapped toplevels to newToplevels
		for(WLCToplevel toplevel : toplevels) {
			boolean mapped = toplevel.isMapped();
			if(mapped && !toplevel.wasMapped) {
				newToplevels.add(toplevel);
			}
			toplevel.wasMapped = mapped;
		}
		
		// Update focus order of toplevels
		updateFocusOrder();
		
		// Do client frame callbacks
		for(WLCSurface surface : surfaces) {
			surface.sendFrame();
		}
		
		// Flush outgoing display buffers
		flushDisplay(instance);
		
		WindowFramebuffer.endFrame();
		
		profiler.pop();
	}
	
	protected void addSurface(WLCSurface surface) {
		surfaces.add(surface);
	}
	
	protected void deleteSurface(WLCSurface surface) {
		surfaces.remove(surface);
		surface.destroy();
	}
	
	protected void addToplevel(WLCToplevel toplevel) {
		toplevels.add(toplevel);
	}
	
	protected void deleteToplevel(WLCToplevel toplevel) {
		toplevels.remove(toplevel);
	}
	
	protected void addPopup(WLCPopup popup) {
		popups.add(popup);
	}
	
	protected void deletePopup(WLCPopup popup) {
		popups.remove(popup);
	}
	
	public WLCSurface[] getAllSurfaces() {
		return surfaces.toArray(WLCSurface[]::new);
	}
	
	public WLCToplevel[] getNewToplevels() {
		WLCToplevel[] toplevels = newToplevels.toArray(WLCToplevel[]::new);
		newToplevels.clear();
		
		return toplevels;
	}
	
	/*
	public void update() {
		ProfilerFiller profiler = Profiler.get();
		profiler.push("wayland");
		
		// Dispatch wayland client events
		profiler.push("dispatch clients");
		dispatchClients(instance);
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
		
		profiler.push("update surface tree");
		// Create new toplevels when necessary
		// Update surface tree geometry and properties of all toplevels
		for(long handle : toplevelHandles) {
			WLCToplevel toplevel = getOrCreateToplevel(handle);
			WLCSurface root = toplevel.getSurfaceTree();
			toplevel.lastChild = updateSurfaceTree(this.instance, root);
			
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
			popup.lastChild = updateSurfaceTree(this.instance, root);
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
		
		profiler.push("update surface data");
		// Update all surface buffers
		for(WLCAbstractWindow window : allWindows) {
			WLCSurface root = window.getSurfaceTree();
			for(WLCSurface surface = root; surface != null; surface = surface.getNextChild()) {
				updateSurfaceData(instance, surface);
				calculateSubpos(surface);
			}
		}
		profiler.pop();
		
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
		
		updateDmabufs();
		
		updateFocusOrder();
		
		// Do client frame callbacks
		for(WLCSurface surface : surfaces) {
			sendFrame(surface.getHandle());
		}
		
		// Flush outgoing display buffers
		flushDisplay(instance);
		
		profiler.pop();
	}
	*/
	
	public WLCToplevel[] getToplevels() {
		return toplevels.toArray(new WLCToplevel[toplevels.size()]);
	}
	
	public WLCToplevel[] getMappedToplevels() {
		return toplevels.stream().filter((t) -> t.isMapped()).toArray(WLCToplevel[]::new);
	}
	
	public WLCToplevel getToplevel(long handle) {
		return toplevels.stream().filter((w) -> w.getHandle() == handle).findAny().orElse(null);
	}
	
	public WLCPopup[] getPopups() {
		return popups.toArray(new WLCPopup[popups.size()]);
	}
	
	public WLCPopup[] getMappedPopups() {
		return popups.stream().filter((t) -> t.isMapped()).toArray(WLCPopup[]::new);
	}
	
	public String getSocket() {
		return socket(this.instance);
	}
	
	public @Nullable String getX11Display() {
		return x11Display(this.instance);
	}
	
	// Create pointer motion event
	public void sendMotion(double x, double y) {
		pointerMotion(instance, x, y);
	}
	
	// Create pointer motion event
	public void sendMotionRefocus(@Nullable WLCSurface surface, double x, double y) {
		pointerMotionFocus(instance, surface, x, y);
	}
	
	// Send relative pointer motion to surface with pointer focus
	public void sendRelativeMotion(double dx, double dy) {
		pointerRelMotion(instance, dx, dy);
	}
	
	// Remove pointer focus from all surfaces
	public void sendMotionOutside() {
		pointerLeave(instance);
	}
	
	// Check if there is an active pointer lock on the surface and lock the pointer if yes
	public boolean maybeLockPointer(@NonNull WLCSurface surface) {
		return maybePointerLock(instance, surface);
	}
	
	// Drop an active pointer lock (if any)
	public void unlockPointer() {
		pointerUnlock(instance);
	}
	
	// Create pointer button event. `button` has to be the linux button code, state is 1 for pressed, 0 for released
	public int sendButton(int button, int state) {
		return pointerButton(instance, button, state);
	}
	
	// Create pointer axis event. `axis` is the scroll axis (0 for vertical, 1 for horizontal)
	public void sendScroll(int axis, double value) {
		pointerAxis(instance, axis, value);
	}
	
	// Get active cursor shape
	public CursorShape getCursorShape() {
		return CursorShape.fromId(cursorShape(instance));
	}
	
	// Set keyboard focus to a toplevel
	public void focusSurface(@Nullable WLCToplevel toplevel) {
		keyboardFocus(instance, toplevel);
		
		// Make toplevel most recently focused
		if(toplevel != null) {
			focusOrder.remove(toplevel);
			focusOrder.addLast(toplevel);
		}
	}
	
	// Mark keyboard as active, forward any pressed modifiers and pressed keys, etc. to the clients
	public void activateKeyboard() {
		keyboardActivate(instance);
	}
	
	// Mark keyboard as inactive, don't forward any keyboard state to the clients
	public void deactivateKeyboard() {
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
		updateFocusOrder();
		return focusOrder.peekLast();
	}
	
	// Find the most recently focused toplevel that exists
	public Stream<WLCToplevel> getMostToLeastRecentFocus() {
		updateFocusOrder();
		return focusOrder.reversed().stream();
	}
	
	public void pressKey(int scancode) {
		keyboardInput(instance, scancode, 1);
	}
	
	public void releaseKey(int scancode) {
		keyboardInput(instance, scancode, 0);
	}
	
	// Update internal key state
	public void internalKeyUpdate(int scancode, boolean pressed) {
		keyboardUpdate(instance, scancode, pressed);
	}
	
	// Resize toplevel interactively (due to user pointer movement)
	public void resizeToplevelInteractive(WLCToplevel toplevel, int width, int height) {
		toplevelResize(instance, toplevel, width, height, true);
	}
	
	// Resize toplevel normally (one-shot)
	public void resizeToplevel(WLCToplevel toplevel, int width, int height) {
		toplevelResize(instance, toplevel, width, height, false);
	}
	
	// Resize toplevel while keeping fullscreen/maximized state
	public void resizeToplevelOverride(WLCToplevel toplevel, int width, int height) {
		toplevelResizeOvr(instance, toplevel, width, height);
	}
	
	public void maximizeToplevel(WLCToplevel toplevel) {
		toplevelMaximize(instance, toplevel);
	}
	
	public void fullscreenToplevel(WLCToplevel toplevel) {
		toplevelFullscreen(instance, toplevel);
	}
	
	public void resizeOutput(int width, int height) {
		outputResize(instance, width, height);
	}
	
	public void setOutputBounds(int width, int height) {
		outputSetBounds(instance, width, height);
	}
	
	public Size getOutputSize() {
		int[] size = outputSize(instance);
		return new Size(size[0], size[1]);
	}
	
	public Size getOutputBounds() {
		int[] size = outputBounds(instance);
		return new Size(size[0], size[1]);
	}
	
	public RawDesktopEntry loadDesktopEntry(File path) {
//		return loadDesktopEntry(instance, path.getAbsolutePath());
		return null;
	}
	
	public RawDesktopEntry[] loadSystemDesktopEntries() {
//		return loadDesktopEntries(instance);
		return new RawDesktopEntry[] {};
	}
	
	public boolean renderSVG(File file, int width, int height, long bufferPtr) {
//		return renderSVG(file.getAbsolutePath(), width, height, bufferPtr);
		return false;
	}
	
	public boolean execApp(String appId) {
//		return execApp(instance, appId);
		return true;
	}
	
	public void setPreferredTerminal(String cmd) {
//		setPreferredTerminal(instance, cmd);
	}
	
	public boolean setKeymapFromStr(String keymap) {
		return setKeymapFromStr(instance, keymap);
	}
	
	public void dndCancel() {
		dndCancel(instance);
	}
	
	public void dndDrop() {
		dndDrop(instance);
	}
	
	public void sendDndMotion(@Nullable WLCSurface surface, double x, double y) {
		dndMotion(instance, surface, x, y);
	}
	
	public WLCSurface getDndIcon() {
		return dndIcon(instance);
	}
	
	private void callOnMaximize(WLCToplevel toplevel) {
		if(requestHandlers.maximizeHandler != null) requestHandlers.maximizeHandler.onMaximizeRequest(toplevel);
	}
	
	private void callOnUnmaximize(WLCToplevel toplevel) {
		if(requestHandlers.unmaximizeHandler != null) requestHandlers.unmaximizeHandler.onUnmaximizeRequest(toplevel);
	}
	
	private void callOnFullscreen(WLCToplevel toplevel) {
		if(requestHandlers.fullscreenHandler != null) requestHandlers.fullscreenHandler.onFullscreenRequest(toplevel);
	}
	
	private void callOnUnfullscreen(WLCToplevel toplevel) {
		if(requestHandlers.unfullscreenHandler != null) requestHandlers.unfullscreenHandler.onUnfullscreenRequest(toplevel);
	}
	
	private void callOnMinimize(WLCToplevel toplevel) {
		if(requestHandlers.minimizeHandler != null) requestHandlers.minimizeHandler.onMinimizeRequest(toplevel);
	}
	
	private void callOnMove(WLCToplevel toplevel, int serial) {
		if(requestHandlers.moveHandler != null) requestHandlers.moveHandler.onMoveRequest(toplevel, serial);
	}
	
	private void callOnResize(WLCToplevel toplevel, int serial, int edges) {
		if(requestHandlers.resizeHandler != null) requestHandlers.resizeHandler.onResizeRequest(toplevel, serial, edges);
	}
	
	private void callOnDND(int serial) {
		if(requestHandlers.dndHandler != null) requestHandlers.dndHandler.onDNDRequest(serial);
	}
	
	public static record Size(int width, int height) {}
	
	public static record ResizeRequest(int serial, int edges) {}
	
	/* Additional bridge native functions
	 * 
	 * Some native methods are directly on various objects like WLCSurface#checkInputRegion, ...
	 * Some functionality is implemented directly in native code and calls java code
	 * The remaining stuff is here:
	 */
	
	/* General bridge functions */
	private static native WaylandCraftBridge init(@Nullable DmabufFeedbackData dmabufFeedbackData);
	private static native void shutdown(long instance);
	private static native void dispatchClients(long instance);
	private static native void flushDisplay(long instance);
	private static native String socket(long instance);
	private static native String x11Display(long instance);
	
	/* Direct Rendering Manager functionality */
	private static native long drmDeviceByPath(String path);
	private static native long drmDeviceByMajorMinor(int major, int minor);
	
	/* Seat functionality */
	private static native void pointerMotion(long instance, double x, double y);
	private static native void pointerMotionFocus(long instance, @Nullable WLCSurface surface, double x, double y);
	private static native void pointerRelMotion(long instance, double dx, double dy);
	private static native boolean maybePointerLock(long instance, @NonNull WLCSurface surface);
	private static native void pointerUnlock(long instance);
	private static native void pointerLeave(long instance);
	private static native int pointerButton(long instance, int button, int state);
	private static native void pointerAxis(long instance, int axis, double value);
	private static native int cursorShape(long instance);
	private static native void keyboardFocus(long instance, @Nullable WLCToplevel toplevel);
	private static native void keyboardActivate(long instance);
	private static native void keyboardDeactivate(long instance);
	private static native void keyboardInput(long instance, int scancode, int action);
	private static native void keyboardUpdate(long instance, int scancode, boolean pressed);
	private static native boolean setKeymapFromStr(long instance, String keymap);
	
	/* Shell functionality */
	private static native void toplevelResize(long instance, WLCToplevel toplevel, int width, int height, boolean interactive);
	private static native void toplevelResizeOvr(long instance, WLCToplevel toplevel, int width, int height);
	private static native void toplevelMaximize(long instance, WLCToplevel toplevel);
	private static native void toplevelFullscreen(long instance, WLCToplevel toplevel);
	
	/* Output functionality */
	private static native int[] outputSize(long instance);
	private static native int[] outputBounds(long instance);
	private static native void outputResize(long instance, int width, int height);
	private static native void outputSetBounds(long instance, int width, int height);
	
	/* Drag and Drop functionality */
	private static native void dndCancel(long instance);
	private static native void dndDrop(long instance);
	private static native void dndMotion(long instance, @Nullable WLCSurface surface, double x, double y);
	private static native WLCSurface dndIcon(long instance);
	
	
	// TODO: Implement the following stuff (or alternatives to them):
	/*
	private static native long[] popups(long instance);
	private static native long popupSurface(long instance, long topLevelHandle);
	// Query the parent of a popup
	// Returned handle is a handle either to a toplevel or another popup
	private static native long popupParent(long instance, long topLevelHandle);
	// Query popup local offset coordinates
	// Returns two-element list containing x,y
	private static native int[] popupOffset(long popupHandle);
	
	private static native RawDesktopEntry loadDesktopEntry(long instance, String path);
	private static native RawDesktopEntry[] loadDesktopEntries(long instance);
	
	private static native boolean renderSVG(String path, int width, int height, long bufferPtr);
	
	private static native boolean execApp(long instance, String appId);
	private static native void setPreferredTerminal(long instance, String cmd);
	*/
	
}
