package dev.evvie.waylandcraft.polymer.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.datafixers.util.Pair;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Polymer's own equipment-packet substitution (PacketPatcher.replace(), see
 * Polymer source) only fires when the equipped ENTITY itself implements
 * PolymerEntity — i.e. a custom mob standing in for a vanilla one. A real
 * player holding a polymer item (like our WindowItem) isn't a PolymerEntity,
 * so that substitution never runs for them. Polymer's LivingEntityMixin only
 * attaches entity context for visibility checks — it doesn't touch the item.
 *
 * The result: when a player switches to holding a polymer item WHILE another
 * player is already tracking them (the ongoing "live" equipment-change path,
 * as opposed to the initial pairing/spawn path Polymer DOES handle via
 * ServerEntityMixin), the raw, unsubstituted item goes out to everyone,
 * tracking-aware or not. A non-aware client has no registry entry or model
 * for it (it's deliberately excluded from registry-sync), so it renders as
 * the missing-model checkerboard.
 *
 * This redirects the same broadcast call Polymer's own LivingEntityMixin
 * targets, but only when at least one of the changed slots actually holds a
 * polymer item — otherwise it falls through to the normal vanilla broadcast
 * unchanged, so this has no effect on unrelated equipment updates. When a
 * polymer item IS present, instead of one shared broadcast packet, each
 * player in the same dimension gets their own packet built via
 * PolymerItemUtils.getPolymerItemStack(), the same substitution path
 * WindowItem.getPolymerItem()/modifyBasePolymerItemStack() already go
 * through for every other Polymer-aware packet.
 *
 * This sends to every player in the dimension rather than only actual chunk
 * trackers, since reliably enumerating chunk-trackers from a LivingEntity
 * mixin context would mean depending on internal, mapping-fragile chunk-map
 * APIs. Vanilla clients silently ignore entity-targeted packets for entities
 * they aren't currently tracking, so this is safe — just slightly more
 * network traffic than the minimum, negligible at normal player counts.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEquipmentMixin {

	@Redirect(method = "handleEquipmentChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;sendToTrackingPlayers(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/Packet;)V"))
	private void waylandcraft$polymerAwareEquipmentBroadcast(ServerChunkCache chunkCache, Entity entity, Packet<? super ClientGamePacketListener> packet) {
		if (!(packet instanceof ClientboundSetEquipmentPacket equipPacket) || !(entity.level() instanceof ServerLevel serverLevel)
				|| PolymerEntity.get(entity) != null) {
			// Real PolymerEntity-type mobs are already handled correctly by
			// Polymer's own PacketPatcher — leave those alone so we don't
			// bypass whatever entity-specific equipment logic other mods
			// rely on there. We only need to fill the gap for entities
			// Polymer doesn't already cover, like real players.
			chunkCache.sendToTrackingPlayers(entity, packet);
			return;
		}

		boolean anyPolymerItem = false;
		for (Pair<EquipmentSlot, ItemStack> pair : equipPacket.getSlots()) {
			if (PolymerItemUtils.isPolymerServerItem(pair.getSecond(), null)) {
				anyPolymerItem = true;
				break;
			}
		}
		if (!anyPolymerItem) {
			// Common case — no polymer items involved, no need to touch
			// vanilla's normal (single, shared) broadcast at all.
			chunkCache.sendToTrackingPlayers(entity, packet);
			return;
		}

		var lookup = serverLevel.registryAccess();
		for (ServerPlayer viewer : serverLevel.players()) {
			if (viewer == entity) {
				// Vanilla doesn't use this packet to inform you about your
				// own held/worn items — only other players' renders need it.
				continue;
			}

			PacketContext context = viewer.connection.getPacketContext();
			List<Pair<EquipmentSlot, ItemStack>> substituted = new ArrayList<>();
			for (Pair<EquipmentSlot, ItemStack> pair : equipPacket.getSlots()) {
				ItemStack stack = pair.getSecond();
				ItemStack visible = PolymerItemUtils.isPolymerServerItem(stack, context)
						? PolymerItemUtils.getPolymerItemStack(stack, context, lookup)
						: stack;
				substituted.add(new Pair<>(pair.getFirst(), visible));
			}

			viewer.connection.send(new ClientboundSetEquipmentPacket(equipPacket.getEntity(), substituted));
		}
	}
}
