package dev.evvie.waylandcraft.item;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WindowItem extends Item {
	
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WaylandCraftCommon.MOD_ID);
	private static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WaylandCraftCommon.MOD_ID);
	
	public static final DeferredItem<WindowItem> WINDOW = ITEMS.registerItem("window", WindowItem::new);
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<WindowHandle>> WINDOW_HANDLE = DATA_COMPONENTS.registerComponentType("window_handle", (builder) -> builder.persistent(WindowHandle.CODEC));
	
	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
		DATA_COMPONENTS.register(modBus);
	}
	
	// NeoForge port: 26.1's USE_EFFECTS component (no slowdown while using the item)
	// has no 1.21.1 equivalent, so players move slowly while placing a window.
	public WindowItem(Properties properties) {
		super(properties);
	}
	
	// Keep the item "in use" until released, like a bow; onUseTick drives window placement
	@Override
	public int getUseDuration(ItemStack itemStack, LivingEntity entity) {
		return 72000;
	}
	
	@Override
	public Component getName(ItemStack itemStack) {
		WindowItemInteractionProvider provider = WaylandCraftCommon.instance.windowItemInteractionProvider;
		if(provider == null) return super.getName(itemStack);
		
		return provider.getName(itemStack);
	}
	
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack item = player.getItemInHand(interactionHand);
		WindowItemInteractionProvider provider = WaylandCraftCommon.instance.windowItemInteractionProvider;
		
		if(provider != null && !provider.isValid(item)) return InteractionResultHolder.pass(item);
		
		player.startUsingItem(interactionHand);
		return InteractionResultHolder.consume(item);
	}
	
	@Override
	public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int i) {
		if(!level.isClientSide()) return;
		
		WindowItemInteractionProvider provider = WaylandCraftCommon.instance.windowItemInteractionProvider;
		if(provider != null) {
			provider.useTick(livingEntity, itemStack);
		}
	}
	
}
