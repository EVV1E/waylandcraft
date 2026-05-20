package dev.evvie.waylandcraft.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;

public class WindowItem extends Item {
	
	public static Item WINDOW;
	public static ResourceKey<Item> WINDOW_RESOURCE_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "window"));
	public static DataComponentType<Long> WINDOW_HANDLE;
	public static Component BROKEN_WINDOW_TEXT = Component.literal("Broken Window");
	public static Component UNKNOWN_WINDOW_TEXT = Component.literal("Unknown Window");
	
	public static void register() {
		WINDOW = Registry.register(BuiltInRegistries.ITEM, WINDOW_RESOURCE_KEY, new WindowItem());
		WINDOW_HANDLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "window_handle"), DataComponentType.<Long>builder().persistent(Codec.LONG).build());
		ItemTooltipCallback.EVENT.register(WindowItem::addTooltip);
	}
	
	public WindowItem() {
		super(new Properties().setId(WINDOW_RESOURCE_KEY).component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0f)));
	}
	
	@Nullable
	public static WLCToplevel getToplevel(ItemStack item) {
		if(item == null) return null;
		
		Long data = item.get(WINDOW_HANDLE);
		if(data == null) return null;
		
		long handle = data.longValue();
		return WaylandCraft.instance.bridge.getToplevel(handle);
	}
	
	@Override
	public Component getName(ItemStack itemStack) {
		WLCToplevel toplevel = getToplevel(itemStack);
		if(toplevel == null) return BROKEN_WINDOW_TEXT;
		
		DesktopEntry entry = WaylandCraft.instance.xdgManager.forAppId(toplevel.appID);
		if(entry == null) return UNKNOWN_WINDOW_TEXT;
		
		String name = entry.name;
		if(name == null) return UNKNOWN_WINDOW_TEXT;
		
		return Component.literal(name);
	}
	
	private static void addTooltip(ItemStack itemStack, TooltipContext ctx, TooltipFlag flag, List<Component> list) {
		Long handle = itemStack.get(WINDOW_HANDLE);
		if(handle != null) {
			String text = "Handle 0x" + Long.toHexString(handle.longValue());
			Component component = Component
					.literal(text)
					.withStyle(ChatFormatting.GRAY);
			list.add(component);
		}
	}
	
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack item = player.getItemInHand(interactionHand);
		WLCToplevel toplevel = getToplevel(item);
		
		if(toplevel == null) return InteractionResult.PASS;
		
		player.startUsingItem(interactionHand);
		return InteractionResult.CONSUME;
	}
	
	@Override
	public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int i) {
		if(!level.isClientSide()) return;
		if(livingEntity != Minecraft.getInstance().player) return;
		
		WaylandCraft.instance.startUsingWindowItem();
	}
	
	public static ItemStack createItem(WLCToplevel toplevel) {
		ItemStack stack = new ItemStack(WindowItem.WINDOW, 1);
		stack.set(WINDOW_HANDLE, toplevel.getHandle());
		return stack;
	}
	
}
