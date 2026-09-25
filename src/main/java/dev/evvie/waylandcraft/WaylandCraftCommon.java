package dev.evvie.waylandcraft;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;

import dev.evvie.waylandcraft.item.ServerItemManager;
import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.item.WindowItemInteractionProvider;
import dev.evvie.waylandcraft.network.WaylandCraftNetworking;
import dev.evvie.waylandcraft.sharing.SharingServer;
import dev.evvie.waylandcraft.sharing.TestPatternSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(WaylandCraftCommon.MOD_ID)
public class WaylandCraftCommon {
	
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static WaylandCraftCommon instance;
	
	public @Nullable WindowItemInteractionProvider windowItemInteractionProvider = null;
	public ServerItemManager serverItemManager = new ServerItemManager();
	public SharingServer sharingServer = new SharingServer();
	
	/* /waylandcraft testpattern [stop] (operators): a server-generated shared window with
	 * video and audio test signals, for checking window sharing without a second player.
	 * See TestPatternSource.
	 */
	private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(MOD_ID)
			.requires((source) -> source.hasPermission(2))
			.then(Commands.literal("testpattern")
				.executes((context) -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					long handle = sharingServer.startTestPattern(player);
					
					ItemStack item = new ItemStack(WindowItem.WINDOW.get());
					item.set(WindowItem.WINDOW_HANDLE, new WindowHandle(TestPatternSource.OWNER, handle));
					item.set(DataComponents.CUSTOM_NAME, Component.literal("Test Pattern " + handle));
					player.addItem(item);
					
					context.getSource().sendSuccess(() -> Component.literal(
						"Test pattern " + handle + " is floating in front of you; its item can go in an item frame too. "
						+ "The border flashes on each beep (sync), and beeps alternate left and right (stereo)."), false);
					return 1;
				})
				.then(Commands.literal("stop").executes((context) -> {
					int count = sharingServer.stopTestPatterns();
					context.getSource().sendSuccess(() -> Component.literal("Stopped " + count + " test pattern(s)"), false);
					return count;
				}))));
	}
	
	public WaylandCraftCommon(IEventBus modBus) {
		instance = this;
		WindowItem.register(modBus);
		modBus.addListener(WaylandCraftNetworking::register);
		
		NeoForge.EVENT_BUS.addListener(LevelTickEvent.Pre.class, (event) -> {
			if(event.getLevel() instanceof ServerLevel level) {
				serverItemManager.onStartTick(level);
				sharingServer.tick(level);
			}
		});
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, (event) -> registerCommands(event.getDispatcher()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, (event) -> {
			if(event.getEntity() instanceof ServerPlayer player) sharingServer.onLogout(player);
		});
	}
	
}
