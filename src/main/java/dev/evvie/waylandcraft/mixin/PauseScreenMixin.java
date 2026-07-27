package dev.evvie.waylandcraft.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.gui.IconRowUtils;
import dev.evvie.waylandcraft.gui.WaylandCraftSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Mixin(PauseScreen.class)
public class PauseScreenMixin extends Screen {

	protected PauseScreenMixin() {
		super(null);
	}

	@Inject(method = "createPauseMenu", at = @At("TAIL"))
	public void addWaylandCraftButton(CallbackInfo info) {
		List<AbstractWidget> row = IconRowUtils.findIconRow(this.children());

		SpriteIconButton button = SpriteIconButton
				.builder(Component.literal("waylandcraft"), (_) -> {Minecraft.getInstance().gui.setScreen(new WaylandCraftSettingsScreen(WaylandCraft.instance));}, true)
				.sprite(Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "logo"), 16, 16)
				.width(IconRowUtils.ICON_ROW_BUTTON_WIDTH)
				.build();

		if(row.isEmpty()) {
			button.setPosition(width / 2 - 124, height / 4 + 72 + 6);
			this.addRenderableWidget(button);
			return;
		}

		IconRowUtils.joinRow(row, button);
		this.addRenderableWidget(button);
	}

}
