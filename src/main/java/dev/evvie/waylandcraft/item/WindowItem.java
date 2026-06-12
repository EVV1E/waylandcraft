package dev.evvie.waylandcraft.item;

import com.mojang.serialization.Codec;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;

public class WindowItem extends Item {
	
	public static Item WINDOW;
	public static ResourceKey<Item> WINDOW_RESOURCE_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window"));
	public static DataComponentType<Long> WINDOW_HANDLE;
	
	public static void register() {
		WINDOW = Registry.register(BuiltInRegistries.ITEM, WINDOW_RESOURCE_KEY, new WindowItem());
		WINDOW_HANDLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "window_handle"), DataComponentType.<Long>builder().persistent(Codec.LONG).build());
	}
	
	public WindowItem() {
		super(new Properties().setId(WINDOW_RESOURCE_KEY).component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0f)));
	}
	
	@Override
	public Component getName(ItemStack itemStack) {
		WindowItemInteractionProvider provider = WaylandCraftCommon.instance.windowItemInteractionProvider;
		if(provider == null) return super.getName(itemStack);
		
		return provider.getName(itemStack);
	}
	
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack item = player.getItemInHand(interactionHand);
		WindowItemInteractionProvider provider = WaylandCraftCommon.instance.windowItemInteractionProvider;
		
		if(provider != null && !provider.isValid(item)) return InteractionResult.PASS;
		
		player.startUsingItem(interactionHand);
		return InteractionResult.CONSUME;
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
