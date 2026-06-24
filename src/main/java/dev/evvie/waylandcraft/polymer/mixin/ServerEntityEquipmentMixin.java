package dev.evvie.waylandcraft.polymer.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.datafixers.util.Pair;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Same gap as LivingEntityEquipmentMixin, but for the OTHER path: the
 * initial equipment send that happens when a player starts tracking an
 * already-equipped entity (e.g. on (re)connect — see ServerEntity's own
 * sendPairingData). Polymer's own re-send of this packet, in its
 * ServerEntityMixin, is ALSO gated behind PolymerEntity.get(entity) != null
 * — same gap as the live-update path, just on the pairing side, so it never
 * fires for a real player holding a polymer item either.
 *
 * Unlike the live-update case, sendPairingData already hands us the exact
 * target player directly, so there's no need to enumerate trackers — just
 * resend a freshly-substituted packet straight to that one player at the
 * tail of the method, mirroring exactly what Polymer's own tail-inject does
 * for PolymerEntity mobs, minus that gate.
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityEquipmentMixin {

	@Shadow @Final private Entity entity;

	@Inject(method = "sendPairingData", at = @At("TAIL"))
	private void waylandcraft$polymerAwareInitialEquipment(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> sender, CallbackInfo ci) {
		if (!(this.entity instanceof LivingEntity livingEntity) || PolymerEntity.get(this.entity) != null) {
			// Real PolymerEntity-type mobs are already handled by Polymer's
			// own tail-inject in ServerEntityMixin — don't double-send for
			// those, just fill the gap for entities Polymer doesn't cover.
			return;
		}

		boolean anyPolymerItem = false;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (PolymerItemUtils.isPolymerServerItem(livingEntity.getItemBySlot(slot), null)) {
				anyPolymerItem = true;
				break;
			}
		}
		if (!anyPolymerItem) return;

		var lookup = livingEntity.level().registryAccess();
		PacketContext context = player.connection.getPacketContext();
		List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = livingEntity.getItemBySlot(slot);
			if (stack.isEmpty()) continue;
			ItemStack visible = PolymerItemUtils.isPolymerServerItem(stack, context)
					? PolymerItemUtils.getPolymerItemStack(stack, context, lookup)
					: stack;
			list.add(new Pair<>(slot, visible));
		}

		sender.accept(new ClientboundSetEquipmentPacket(this.entity.getId(), list));
	}
}
