package dev.evvie.waylandcraft.neoforge;

import org.lwjgl.system.Platform;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge;
import dev.evvie.waylandcraft.render.WindowShaders;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = WaylandCraftCommon.MOD_ID, dist = Dist.CLIENT)
public class WaylandCraftNeoForgeClient {
	
	// NeoForge port step 4: drives only the compositor bridge. The Fabric
	// client (WaylandCraft) takes over this lifecycle once it is ported.
	private WaylandCraftBridge bridge = null;
	private int lastToplevelCount = 0;
	
	public WaylandCraftNeoForgeClient(IEventBus modBus) {
		modBus.addListener(RegisterShadersEvent.class, WindowShaders::register);
		
		if(Platform.get() != Platform.LINUX) {
			WaylandCraftCommon.LOGGER.error("Invalid platform detected! Most mod features will be disabled");
			return;
		}
		
		NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, (event) -> update());
	}
	
	private void update() {
		if(bridge == null) {
			bridge = WaylandCraftBridge.start();
			WaylandCraftCommon.LOGGER.info("Wayland server started on " + bridge.getSocket());
			WaylandCraftCommon.LOGGER.info("Xwayland started on " + bridge.getX11Display());
		}
		bridge.update();
		
		int toplevelCount = bridge.getMappedToplevels().length;
		if(toplevelCount != lastToplevelCount) {
			lastToplevelCount = toplevelCount;
			WaylandCraftCommon.LOGGER.info("Mapped toplevels: " + toplevelCount);
		}
	}
	
}
