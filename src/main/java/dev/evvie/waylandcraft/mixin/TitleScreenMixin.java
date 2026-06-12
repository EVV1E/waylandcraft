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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
	
	protected TitleScreenMixin() {
		super(null);
	}
	
	@Inject(method = "init", at = @At("TAIL"))
	public void addWaylandCraftButton(CallbackInfo info, @Local(ordinal = 3) int topPos) {
		SpriteIconButton button = SpriteIconButton
				.builder(Component.literal("waylandcraft"), (_) -> {Minecraft.getInstance().setScreen(new WaylandCraftSettingsScreen(WaylandCraft.instance));}, true)
				.sprite(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "logo"), 16, 16)
				.width(20)
				.build();
		button.setPosition(width / 2 - 124, topPos - 36);
		this.addRenderableWidget(button);
	}
	
}
