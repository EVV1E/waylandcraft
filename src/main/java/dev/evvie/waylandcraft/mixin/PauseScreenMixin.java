package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.gui.WaylandCraftSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Mixin(PauseScreen.class)
public class PauseScreenMixin extends Screen {
	
	protected PauseScreenMixin() {
		super(null);
	}
	
	private SpriteIconButton button;
	
	@Inject(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"))
	public void addButton(CallbackInfo info, @Local GridLayout layout) {
		button = SpriteIconButton
				.builder(Component.literal("waylandcraft"), (_) -> {Minecraft.getInstance().setScreen(new WaylandCraftSettingsScreen(WaylandCraft.instance));}, true)
				.sprite(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "logo"), 16, 16)
				.width(20)
				.build();
		layout.addChild(button, 3, 0);
	}
	
	@Inject(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;visitWidgets(Ljava/util/function/Consumer;)V"))
	public void offsetButton(CallbackInfo info) {
		button.setX(button.getX() - 24);
	}
	
}
