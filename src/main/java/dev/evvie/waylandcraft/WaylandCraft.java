package dev.evvie.waylandcraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.bridge.WLCPopup;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.ResizeRequest;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.Size;
import dev.evvie.waylandcraft.desktop.XDGDesktopManager;
import dev.evvie.waylandcraft.displays.WindowDisplay;
import dev.evvie.waylandcraft.displays.WindowDisplay.DisplayHitResult;
import dev.evvie.waylandcraft.grabs.DNDGrab;
import dev.evvie.waylandcraft.grabs.MoveGrab;
import dev.evvie.waylandcraft.grabs.PointerGrabMap;
import dev.evvie.waylandcraft.grabs.PointerGrabMap.ImplicitGrab;
import dev.evvie.waylandcraft.grabs.ResizeGrab;
import dev.evvie.waylandcraft.gui.AppLauncherScreen;
import dev.evvie.waylandcraft.gui.WaylandHudRenderer;
import dev.evvie.waylandcraft.gui.WindowManagerScreen;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.item.WindowItemManager;
import dev.evvie.waylandcraft.render.RenderUtils;
import dev.evvie.waylandcraft.render.WindowInHandRenderer;
import dev.evvie.waylandcraft.render.WindowInItemFrameRenderer;
import dev.evvie.waylandcraft.render.model.WindowItemModel;
import dev.evvie.waylandcraft.render.WindowShaders;
import dev.evvie.waylandcraft.settings.WaylandCraftSettings;
import dev.evvie.waylandcraft.settings.WaylandCraftSettingsManager;
import dev.evvie.waylandcraft.utils.CursorShape;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = WaylandCraftCommon.MOD_ID, dist = Dist.CLIENT)
public class WaylandCraft {
	
	private static final String KEYBIND_CATEGORY = "key.category.waylandcraft.keys";
	
	public static WaylandCraft instance;
	public static boolean fallbackMode = false;
	
	public WaylandCraftSettingsManager settingsManager;
	public WaylandCraftSettings settings;
	
	public WaylandCraftBridge bridge = null;
	public String waylandSocket = "";
	public @Nullable String x11Display = null;
	
	public ArrayList<WindowDisplay> displays = new ArrayList<WindowDisplay>();
	
	public boolean overridePickBlock = false;
	public HitResult trueGameHitResult = null;
	
	public WLCToplevel pinnedToplevel = null;
	
	public WindowItemManager itemManager = new WindowItemManager();
	public XDGDesktopManager xdgManager;
	
	public KeyMapping keyOpenScreen = new KeyMapping("waylandcraft.key.windowManager", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, KEYBIND_CATEGORY);
	public KeyMapping keyOpenAppLauncher = new KeyMapping("waylandcraft.key.appLauncher", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEYBIND_CATEGORY);
	public KeyMapping keyCaptureKeyboard = new KeyMapping("waylandcraft.key.captureKeyboard", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEYBIND_CATEGORY);
	
	// Window displays are drawn in their own batch so vanilla's buffered render types keep their order
	private final MultiBufferSource.BufferSource worldBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
	
	public WindowInHandRenderer windowInHandRenderer = new WindowInHandRenderer();
	public WindowInItemFrameRenderer windowInItemFrameRenderer = new WindowInItemFrameRenderer();
	public WaylandHudRenderer hudRenderer = new WaylandHudRenderer(this);
	
	public PointerGrabMap pointerGrabs = new PointerGrabMap(this);
	
	// HitResult of currently hovered WindowDisplay
	// Only non-null, when no exclusive pointer grabs are currently active
	public DisplayHitResult hoveredDisplay = null;
	
	public KeyboardCaptureMode keyboardCaptureMode = KeyboardCaptureMode.NONE;
	
	public PointerCapture pointerCapture = null;
	
	private boolean playerUsingWindowItem = false;
	private boolean playerWasUsingWindowItem = false;
	
	public @Nullable CursorShape cursorShape = null;
	
	public WaylandCraft(IEventBus modBus) {
		WaylandCraftCommon.LOGGER.info("Initializing WaylandCraft");
		
		instance = this;
		
		modBus.addListener(RegisterKeyMappingsEvent.class, (event) -> {
			event.register(keyOpenScreen);
			event.register(keyOpenAppLauncher);
			event.register(keyCaptureKeyboard);
		});
		modBus.addListener(RegisterShadersEvent.class, WindowShaders::register);
		modBus.addListener(RegisterShadersEvent.class, RenderUtils::registerShaders);
		WindowItemModel.register(modBus);
		
		settingsManager = new WaylandCraftSettingsManager(this);
		
		if(Platform.get() != Platform.LINUX) {
			WaylandCraftCommon.LOGGER.error("Invalid platform detected! Most mod features will be disabled");
			WaylandCraft.fallbackMode = true;
			return;
		}
		
		NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, (event) -> update());
		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::onRenderLevelStage);
		NeoForge.EVENT_BUS.addListener(RenderHandEvent.class, this::onRenderHand);
		NeoForge.EVENT_BUS.addListener(RenderItemInFrameEvent.class, this::onRenderItemInFrame);
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, (event) -> onClientTick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, (event) -> itemManager.onStartTick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, (event) -> onClientJoin(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, (event) -> onClientDisconnect());
		NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, (event) -> addWindowItemTooltip(event.getItemStack(), event.getToolTip()));
		
		WaylandCraftCommon.instance.windowItemInteractionProvider = itemManager;
		
		modBus.addListener(RegisterGuiLayersEvent.class, hudRenderer::register);
	}
	
	/* Update bridge and clients. May be called at any state of the game, even outside of a level
	 * Called after game render in Minecraft::runTick
	 */
	public void update() {
		if(fallbackMode) return;
		
		if(bridge == null) {
			bridge = WaylandCraftBridge.start();
			waylandSocket = bridge.getSocket();
			x11Display = bridge.getX11Display();
			xdgManager = new XDGDesktopManager(this);
			registerSettingsResponders();
			settingsManager.loadKeymap();
			
			WaylandCraftCommon.LOGGER.info("Wayland server started on " + waylandSocket);
			WaylandCraftCommon.LOGGER.info("Xwayland started on " + x11Display);
		}
		bridge.update();
	}
	
	private void registerSettingsResponders() {
		settingsManager.registerResponder(WaylandCraftSettings.TERMINAL_CHOICE, (value) -> {
			bridge.setPreferredTerminal((String) value);
		});
	}
	
	private void onRenderLevelStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
		if(bridge == null) return;
		
		updateWorld();
		
		Vec3 cameraPos = event.getCamera().getPosition();
		displays.forEach((d) -> d.render(event.getPoseStack(), worldBuffers, cameraPos));
		worldBuffers.endBatch();
	}
	
	// called in the pick() method of MinecraftMixin
	public void updatePointer() {
		if(bridge == null) return;
		
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		processPointerMotion(camera);
		
		if(Minecraft.getInstance().player == null || !Minecraft.getInstance().player.isUsingItem()) playerUsingWindowItem = false;
		if(playerUsingWindowItem) {
			ItemStack item = Minecraft.getInstance().player.getUseItem();
			if(item.is(WindowItem.WINDOW)) {
				WLCToplevel toplevel = getToplevel(item);
				
				if(toplevel != null) {
					WindowDisplay display = getOrCreateDisplay(toplevel);
					if(!playerWasUsingWindowItem) {
						display.anchorDistance = 2.0;
					}
					
					display.doGrabMove(camera.getPosition(), new Vec3(camera.getLookVector()), new Vec3(camera.getUpVector()), camera.getYRot());
					
					WaylandCraft.instance.bridge.focusSurface(toplevel);
				}
			}
			else playerUsingWindowItem = false;
		}
		playerWasUsingWindowItem = playerUsingWindowItem;
	}
	
	// First-person window items show the window itself instead of the item
	private void onRenderHand(RenderHandEvent event) {
		ItemStack itemStack = event.getItemStack();
		if(!itemStack.is(WindowItem.WINDOW)) return;
		if(getToplevel(itemStack) == null) return;
		
		event.setCanceled(true);
		
		HumanoidArm mainArm = Minecraft.getInstance().player.getMainArm();
		HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
		windowInHandRenderer.render(event.getPoseStack(), event.getMultiBufferSource(), event.getSwingProgress(), event.getEquipProgress(), event.getPackedLight(), arm, itemStack);
	}
	
	// Item frames holding a window item show the window itself
	private void onRenderItemInFrame(RenderItemInFrameEvent event) {
		WLCToplevel toplevel = getToplevel(event.getItemStack());
		if(toplevel == null) return;
		
		event.setCanceled(true);
		
		// Match the item scale vanilla applies after this event
		PoseStack poseStack = event.getPoseStack();
		poseStack.pushPose();
		poseStack.scale(0.5f, 0.5f, 0.5f);
		windowInItemFrameRenderer.render(toplevel, poseStack, event.getMultiBufferSource());
		poseStack.popPose();
	}
	
	public void updateWorld() {
		for(WLCPopup popup : bridge.getMappedPopups()) {
			WLCAbstractWindow root = popup;
			while((root = ((WLCPopup) root).getParent()) instanceof WLCPopup);
			
			WLCToplevel toplevel = (WLCToplevel) root;
			boolean toplevelHasWindow = hasDisplayFor(toplevel);
			boolean popupHasWindow = hasDisplayFor(popup);
			if(toplevelHasWindow && !popupHasWindow) {
				getOrCreateDisplay(popup);
			}
			else if(!toplevelHasWindow && popupHasWindow) {
				displays.removeIf((w) -> w.window == popup);
			}
		}
		
		displays.removeIf((d) -> !d.isValid());
		displays.forEach((d) -> d.updateGeometry());
		
		for(WLCPopup popup : bridge.getMappedPopups()) {
			anchorToParent(popup);
		}
	}
	
	public void onClientTick(Minecraft minecraft) {
		if(minecraft.player == null) return;
		checkKeybinds(minecraft);
		
		updateDisplayRequests();
		
		itemManager.giveItemsIfMissing(bridge.getNewToplevels());
		
		boolean inWMScreen = Minecraft.getInstance().screen instanceof WindowManagerScreen;
		
		// Make sure the toplevels are focused in their respective order and being refocused when a toplevel disappears
		if(!inWMScreen) {
			WLCToplevel focus = bridge.getMostToLeastRecentFocus()
					.filter((t) -> hasDisplayFor(t))
					.findFirst()
					.orElse(null);
			
			bridge.focusSurface(focus);
		}
		
		updateOutputSize(inWMScreen);
	}
	
	public void startUsingWindowItem() {
		playerUsingWindowItem = true;
	}
	
	public void enableKeyboardCapture(boolean hardCapture) {
		if(keyboardCaptureMode != KeyboardCaptureMode.NONE) return;
		
		keyboardCaptureMode = hardCapture ? KeyboardCaptureMode.HARD_CAPTURE : KeyboardCaptureMode.CAPTURE;
		bridge.activateKeyboard();
	}
	
	public void disableKeyboardCapture() {
		if(keyboardCaptureMode == KeyboardCaptureMode.NONE) return;
		
		keyboardCaptureMode = KeyboardCaptureMode.NONE;
		bridge.deactivateKeyboard();
		disablePointerCapture();
	}
		
	private void checkKeybinds(Minecraft minecraft) {
		if(keyOpenScreen.consumeClick()) {
			disableKeyboardCapture();
			pointerGrabs.releaseAll();
			minecraft.setScreen(new WindowManagerScreen(WaylandCraft.instance));
		}
		else if(keyOpenAppLauncher.consumeClick()) {
			minecraft.setScreen(new AppLauncherScreen(WaylandCraft.instance));
		}
		else if(keyCaptureKeyboard.consumeClick()) {
			enableKeyboardCapture(false);
		}
	}
	
	private void onClientJoin(Minecraft minecraft) {
		if(bridge == null) return;
		minecraft.getChatListener().handleSystemMessage(Component.literal("Wayland compositor running on " + waylandSocket), false);
		if(x11Display != null) minecraft.getChatListener().handleSystemMessage(Component.literal("xwayland-satellite running on " + x11Display), false);
		itemManager.giveItemsIfMissing(bridge.getMappedToplevels());
	}
	
	private void onClientDisconnect() {
		displays.clear();
		itemManager.reset();
	}
	
	@Nullable
	public static WLCToplevel getToplevel(ItemStack item) {
		if(item == null) return null;
		if(WaylandCraft.instance.bridge == null) return null;
		
		WindowHandle data = item.get(WindowItem.WINDOW_HANDLE);
		if(data == null) return null;
		if(!data.matchesPlayer(Minecraft.getInstance().player)) return null;
		
		return WaylandCraft.instance.bridge.getToplevel(data.handle());
	}
	
	private void addWindowItemTooltip(ItemStack itemStack, List<Component> list) {
		WindowHandle handle = itemStack.get(WindowItem.WINDOW_HANDLE);
		if(handle != null) {
			String text = "Handle 0x" + Long.toHexString(handle.handle());
			Component component = Component
					.literal(text)
					.withStyle(ChatFormatting.GRAY);
			list.add(component);
			String owner = "Owner " + handle.player();
			component = Component
					.literal(owner)
					.withStyle(ChatFormatting.GRAY);
			list.add(component);
		}
	}
	
	private void updateDisplayRequests() {
		// Hide all windows that were minimized and unset minimize requested state
		displays.removeIf((w) -> w.window instanceof WLCToplevel && ((WLCToplevel) w.window).requests.minimize);
		Stream.of(bridge.getToplevels()).forEach((t) -> t.requests.minimize = false);
		
		// Handle any maximize or unmaximize requests
		for(WLCToplevel toplevel : bridge.getMappedToplevels()) {
			if(toplevel.requests.maximize && toplevel.requests.unmaximize) {
				// Both requests shouldn't happen at the same time
				toplevel.restoreGeometry = null;
			}
			else if(toplevel.requests.maximize) {
				// Maximize toplevel and store its old geometry
				toplevel.restoreGeometry = toplevel.geometry;
				bridge.maximizeToplevel(toplevel);
			}
			else if(toplevel.requests.unmaximize) {
				// Unmaximize toplevel and attempt to restore old geometry
				SurfaceGeometry newGeometry = toplevel.restoreGeometry;
				if(newGeometry == null) newGeometry = toplevel.geometry;
				
				// resizeToplevel also unsets the maximize flag
				bridge.resizeToplevel(toplevel, newGeometry.width(), newGeometry.height());
				toplevel.restoreGeometry = null;
			}
			
			toplevel.requests.maximize = toplevel.requests.unmaximize = false;
		}
		
		// Handle any fullscreen or unfullscreen requests
		for(WLCToplevel toplevel : bridge.getToplevels()) {
			if(toplevel.requests.fullscreen && toplevel.requests.unfullscreen) {
				// Both requests shouldn't happen at the same time
				toplevel.restoreGeometry = null;
			}
			else if(toplevel.requests.fullscreen) {
				// Fullscreen toplevel and store its old geometry
				toplevel.restoreGeometry = toplevel.geometry;
				bridge.fullscreenToplevel(toplevel);
			}
			else if(toplevel.requests.unfullscreen) {
				// Unfullscreen toplevel and attempt to restore old geometry
				SurfaceGeometry newGeometry = toplevel.restoreGeometry;
				if(newGeometry == null) newGeometry = toplevel.geometry;
				
				// resizeToplevel also unsets the fullscreen flag
				bridge.resizeToplevel(toplevel, newGeometry.width(), newGeometry.height());
				toplevel.restoreGeometry = null;
			}
			
			toplevel.requests.fullscreen = toplevel.requests.unfullscreen = false;
		}
		
		Integer moveRequest = bridge.checkMoveRequest();
		if(moveRequest != null) {
			ImplicitGrab implicit = pointerGrabs.dropImplicitMatching(moveRequest.intValue());
			if(implicit != null) {
				// The serial matched an active implicit grab
				pointerGrabs.startExclusive(new MoveGrab(implicit));
			}
		}
		
		ResizeRequest resizeRequest = bridge.checkResizeRequest();
		if(resizeRequest != null) {
			ImplicitGrab implicit = pointerGrabs.dropImplicitMatching(resizeRequest.serial());
			if(implicit != null) {
				// The serial matched an active implicit grab
				pointerGrabs.startExclusive(new ResizeGrab(implicit, resizeRequest.edges()));
			}
		}
		
		Integer dndRequest = bridge.checkDndRequest();
		if(dndRequest != null) {
			ImplicitGrab implicit = pointerGrabs.dropImplicitMatching(dndRequest);
			if(implicit != null) {
				WaylandCraftCommon.LOGGER.info("DND STARTED");
				// The serial matched an active implicit grab
				pointerGrabs.startExclusive(new DNDGrab(implicit));
			}
			else {
				// Couldn't match implicit grab, have to cancel dnd
				WaylandCraftCommon.LOGGER.info("drag and drop did not match implicit grab");
				bridge.dndCancel();
			}
		}
	}
	
	private void updateOutputSize(boolean inWMScreen) {
		int outputWidth = Minecraft.getInstance().getWindow().getWidth();
		int outputHeight = Minecraft.getInstance().getWindow().getHeight();
		
		Size size = bridge.getOutputSize();
		if(size.width() != outputWidth || size.height() != outputHeight) {
			bridge.resizeOutput(outputWidth, outputHeight);
			if(!inWMScreen) bridge.setOutputBounds(outputWidth, outputHeight);
		}
	}
	
	public @Nullable WindowDisplay getDisplay(WLCAbstractWindow window) {
		return displays.stream().filter((w) -> w.window == window).findAny().orElse(null);
	}
	
	public WindowDisplay getOrCreateDisplay(WLCAbstractWindow window) {
		WindowDisplay display = getDisplay(window);
		if(display != null) return display;
		
		display = new WindowDisplay(window);
		displays.add(display);
		
		return display;
	}
	
	public boolean hasDisplayFor(WLCAbstractWindow window) {
		return getDisplay(window) != null;
	}
	
	public void disablePointerCapture() {
		destroyPointerOverlay();
		if(pointerCapture == null) return;
		if(pointerCapture instanceof LockedPointerCapture) bridge.unlockPointer();
		pointerCapture = null;
	}
	
	public void destroyPointerOverlay() {
		if(Minecraft.getInstance().getOverlay() instanceof PointerCaptureOverlay overlay) {
			overlay.destroy();
			Minecraft.getInstance().setOverlay(null);
		}
	}
	
	private void processPointerMotion(Camera camera) {
		this.cursorShape = null;
		
		if(pointerCapture != null) {
			if(!pointerCapture.isValid()) {
				disablePointerCapture();
				return;
			}
			
			if(pointerCapture instanceof LockedPointerCapture) this.cursorShape = bridge.getCursorShape();
			else this.cursorShape = CursorShape.HIDE;
			
			boolean locked = pointerCapture.surface != null && bridge.maybeLockPointer(pointerCapture.surface);
			boolean detach = settings.getDetachCursor();
			if(pointerCapture instanceof LockedPointerCapture && !locked) {
				PointerCapture old = pointerCapture;
				disablePointerCapture();
				if(detach) {
					pointerCapture = new MotionPointerCapture(old.display, old.pressedButtons);
				}
			}
			else if(pointerCapture instanceof MotionPointerCapture && locked) {
				PointerCapture old = pointerCapture;
				disablePointerCapture();
				if(detach) {
					pointerCapture = new LockedPointerCapture(old.display, old.surface, old.pressedButtons);
				}
			}
			
			return;
		}
		
		// Reset hovered display and pick block override
		this.hoveredDisplay = null;
		this.overridePickBlock = false;
		
		if(Minecraft.getInstance().screen instanceof WindowManagerScreen) {
			return;
		}
		else if(Minecraft.getInstance().screen != null) {
			pointerGrabs.releaseAll();
			bridge.sendMotionOutside();
			return;
		}
		
		Vec3 pos = camera.getPosition();
		Vec3 look = new Vec3(camera.getLookVector());
		Vec3 up = new Vec3(camera.getUpVector());
		
		DisplayHitResult finalHitResult = null;
		double finalDistance = Double.POSITIVE_INFINITY;
		for(WindowDisplay display : displays) {
			DisplayHitResult hit = display.intersect(pos, look);
			if(hit == null || hit.isMiss()) continue;
			
			double dist = hit.position.distanceToSqr(pos);
			if(finalHitResult == null || dist < finalDistance) {
				finalHitResult = hit;
				finalDistance = dist;
			}
		}
		
		// Check if game hit result closer
		// Must use trueGameHitResult because the game hit result is overridden by overridePickBlock
		HitResult gameHitResult = trueGameHitResult;
		double gameHitDistance = (gameHitResult == null || gameHitResult.getType() == HitResult.Type.MISS) ? Double.POSITIVE_INFINITY : gameHitResult.getLocation().distanceToSqr(pos);
		if(gameHitDistance < finalDistance) finalHitResult = null;
		
		// Check for player reach
		if(finalHitResult != null && !finalHitResult.position.closerThan(pos, Minecraft.getInstance().player.blockInteractionRange())) finalHitResult = null;
		
		if(!pointerGrabs.isExclusiveGrabActive()) hoveredDisplay = finalHitResult;
		
		// Check for pointer grab and short-circuit if any
		if(pointerGrabs.isGrabActive()) {
			this.overridePickBlock = true;
			this.cursorShape = bridge.getCursorShape();
			
			pointerGrabs.moveWorld(pos, look, up, camera.getYRot(), camera.getXRot());
			if(finalHitResult != null) {
				pointerGrabs.hover(finalHitResult.target.window, finalHitResult.surface, finalHitResult.surfaceLocalRelative.x, finalHitResult.surfaceLocalRelative.y);
			}
			else {
				pointerGrabs.hoverNone();
			}
			
			return;
		}
		
		/* All of the following code will only be executed when there aren't any active pointer grabs */
		
		if(hoveredDisplay != null && !canStartInteracting()) hoveredDisplay = null;
		
		if(hoveredDisplay != null) {
			this.overridePickBlock = true;
		}
		
		if(hoveredDisplay != null && hoveredDisplay.dist >= 0) {
			WindowDisplay display = hoveredDisplay.target;
			WLCSurface surface = hoveredDisplay.surface;
			Vec3 rel = hoveredDisplay.surfaceLocalRelative;
			
			this.cursorShape = bridge.getCursorShape();
			bridge.sendMotionRefocus(surface, rel.x, rel.y);
			
			if(keyboardCaptureMode != KeyboardCaptureMode.NONE) {
				boolean pointerLocked = bridge.maybeLockPointer(surface);
				if(pointerLocked) {
					pointerCapture = new LockedPointerCapture(display, surface);
				}
				else if(settings.getDetachCursor()) {
					pointerCapture = new MotionPointerCapture(display);
				}
			}
			
			// Focus on hover
			if(settings.getFocusOnHover() && hoveredDisplay.target.window instanceof WLCToplevel toplevel) {
				bridge.focusSurface(toplevel);
			}
		}
		else {
			bridge.sendMotionOutside();
		}
	}
	
	/* Handle mouse button input
	 * Returns true when the mouse button action has been consumed
	 */
	public boolean onButtonPress(long windowHandle, int button, int action, int modifiers) {
		if(bridge == null) return false;
		
		if(pointerCapture != null) {
			if(action == 1 && !pointerCapture.pressedButtons.contains(button)) {
				bridge.sendButton(0x110 + button, 1);
				pointerCapture.pressedButtons.add(button);
			}
			else if(action == 0 && pointerCapture.pressedButtons.contains(button)) {
				bridge.sendButton(0x110 + button, 0);
				pointerCapture.pressedButtons.remove(button);
			}
			else if(action == 0) {
				// Forward release to minecraft if it wasn't part of this pointer capture
				return false;
			}
			return true;
		}
		
		if(action == 0 && pointerGrabs.isGrabActive(button)) {
			pointerGrabs.release(button);
			return true;
		}
		
		if(pointerGrabs.isExclusiveGrabActive()) return true;
		
		// Handle implicit pointer grab button presses
		if(action == 1) {
			// Start new implicit grab when conditions are met
			if(!pointerGrabs.isImplicitActive() && hoveredDisplay != null && hoveredDisplay.dist >= 0) {
				pointerGrabs.startImplicit(hoveredDisplay);
				WLCAbstractWindow window = hoveredDisplay.target.window;
				if(window instanceof WLCToplevel) bridge.focusSurface((WLCToplevel) window);
			}
			
			// If an implicit pointer grab is now active, capture the button press
			if(pointerGrabs.isImplicitActive()) {
				pointerGrabs.sendImplicitButton(button);
				return true;
			}
			
			// If clicking on a window at all, the button press should be captured, even if it wasn't passed on to the application
			if(hoveredDisplay != null) return true;
		}
		
		return false;
	}
	
	private boolean canStartInteracting() {
		LocalPlayer player = Minecraft.getInstance().player;
		if(player == null) return false;
		if(player.isUsingItem()) return false;
		return true;
	}
	
	/* Handle mouse being turned in game
	 * Returns true when the mouse move has been consumed
	 */
	public boolean onMouseTurn(double dx, double dy) {
		if(bridge == null) return false;
		if(pointerCapture == null) return false;
		if(pointerCapture instanceof LockedPointerCapture) bridge.sendRelativeMotion(dx, dy);
		return true;
	}
	
	/* Handle mouse scroll input
	 * Returns true when the mouse scroll action has been consumed
	 */
	public boolean onScroll(long windowHandle, double scrollX, double scrollY) {
		if(bridge == null) return false;
		
		if(playerUsingWindowItem) {
			WLCToplevel toplevel = getToplevel(Minecraft.getInstance().player.getUseItem());
			if(toplevel != null) {
				WindowDisplay display = getDisplay(toplevel);
				if(display != null) {
					display.adjustAnchorDistance(scrollY);
					return true;
				}
			}
		}

		if(pointerGrabs.isExclusiveGrabActive()) {
			pointerGrabs.onScroll(scrollX, scrollY);
			return true;
		}
		
		if(hoveredDisplay != null) {
			if(hoveredDisplay.dist < 0) return true;
			
			bridge.sendScroll(0, -scrollY);
			bridge.sendScroll(1, -scrollX);
			
			WLCAbstractWindow window = hoveredDisplay.target.window;
			if(window instanceof WLCToplevel) bridge.focusSurface((WLCToplevel) window);
			
			return true;
		}
		
		return false;
	}
	
	/* Handle keyboard input
	 * Returns true when the key press action has been consumed
	 * This code just completely naively assumes that the scancode received by GLFW
	 * is also the correct matching Wayland scancode for the default XKBConfig.
	 * For X11 and Wayland hosts, this is a huge hack but should mostly work for now
	 */
	public boolean onKeyPress(long windowHandle, int key, int scancode, int action, int modifiers) {
		if(bridge == null) return false;
		
		if(key == GLFW.GLFW_KEY_Q && modifiers == GLFW.GLFW_MOD_ALT) {
			if(action == 0) return true;
			
			if(keyboardCaptureMode != KeyboardCaptureMode.HARD_CAPTURE) {
				enableKeyboardCapture(true);
			}
			else {
				disableKeyboardCapture();
			}
			return true;
		}
		
		if(keyboardCaptureMode == KeyboardCaptureMode.NONE) return false;
		
		if(keyboardCaptureMode == KeyboardCaptureMode.CAPTURE && key == GLFW.GLFW_KEY_ESCAPE) {
			disableKeyboardCapture();
			return true;
		}
		
		if(action == GLFW.GLFW_PRESS) {
			bridge.pressKey(scancode);
		}
		else if(action == GLFW.GLFW_RELEASE) {
			bridge.releaseKey(scancode);
		}
		
		return true;
	}
	
	public static int correctScancode(int scancode) {
		if(GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			scancode += 8;
		}
		return scancode;
	}
	
	private void anchorToParent(WLCPopup popup) {
		WindowDisplay window = displays.stream().filter((w) -> w.window == popup).findAny().orElse(null);
		WindowDisplay parent = displays.stream().filter((w) -> w.window == popup.getParent()).findAny().orElse(null);
		
		if(window == null || parent == null) return;
		
		// If the parent is also a popup, first make it anchor itself
		if(parent.window instanceof WLCPopup) {
			anchorToParent((WLCPopup) parent.window);
		}
		
		window.rotate(parent.normal(), parent.down());
		window.moveOrigin(parent.localToWorld(popup.offsetX, popup.offsetY, 0.01));
	}
	
	public static enum KeyboardCaptureMode {
		
		NONE, CAPTURE, HARD_CAPTURE;
		
	}
	
	public abstract class PointerCapture {
		
		public final WindowDisplay display;
		public final HashSet<Integer> pressedButtons;
		
		public WLCSurface surface;
		
		public PointerCapture(WindowDisplay display, WLCSurface surface, Collection<Integer> pressedButtons) {
			this.display = display;
			this.surface = surface;
			this.pressedButtons = new HashSet<Integer>(pressedButtons);
		}
		
		public boolean isValid() {
			return displays.contains(display);
		}
		
	}
	
	public class LockedPointerCapture extends PointerCapture {
		
		public LockedPointerCapture(WindowDisplay display, WLCSurface surface, Collection<Integer> pressedButtons) {
			super(display, surface, pressedButtons);
		}
		
		public LockedPointerCapture(WindowDisplay display, WLCSurface surface) {
			this(display, surface, Collections.emptyList());
		}
		
		@Override
		public boolean isValid() {
			return super.isValid() && surface.isAlive();
		}
		
	}
	
	public class MotionPointerCapture extends PointerCapture {
		
		public MotionPointerCapture(WindowDisplay display, Collection<Integer> pressedButtons) {
			super(display, null, pressedButtons);
			
			if(Minecraft.getInstance().getOverlay() == null) Minecraft.getInstance().setOverlay(new PointerCaptureOverlay());
		}
		
		public MotionPointerCapture(WindowDisplay display) {
			this(display, Collections.emptyList());
		}
		
	}
	
	public class PointerCaptureOverlay extends Overlay {
		
		// Set while destroy() regrabs the mouse, so MouseHandlerMixin skips KeyMapping.setAll().
		// Only touched on the render thread (ScopedValue is not available on Java 21).
		public static boolean stopKeyMappingSetAll = false;
		
		public PointerCaptureOverlay() {
			Minecraft.getInstance().mouseHandler.releaseMouse();
		}
		
		public void destroy() {
			stopKeyMappingSetAll = true;
			try {
				Minecraft.getInstance().mouseHandler.grabMouse();
			}
			finally {
				stopKeyMappingSetAll = false;
			}
		}
		
		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
			if(!(pointerCapture instanceof MotionPointerCapture motionCapture)) return;
			
			Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
			Camera.NearPlane plane = camera.getNearPlane();
			MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
			Window window = Minecraft.getInstance().getWindow();
			
			double rx = mouseHandler.xpos() / window.getWidth() * 2 - 1;
			double ry = -(mouseHandler.ypos() / window.getHeight() * 2 - 1);
			
			Vec3 pos = camera.getPosition();
			Vec3 look = plane.getPointOnPlane((float) rx, (float) ry).normalize();
			
			DisplayHitResult result = pointerCapture.display.intersect(pos, look);
			if(result.isMiss()) {
				bridge.sendMotionOutside();
				motionCapture.surface = null;
			}
			else {
				bridge.sendMotionRefocus(result.surface, result.surfaceLocalRelative.x, result.surfaceLocalRelative.y);
				motionCapture.surface = result.surface;
			}
		}
		
		@Override
		public boolean isPauseScreen() {
			return false;
		}
		
	}
	
}

