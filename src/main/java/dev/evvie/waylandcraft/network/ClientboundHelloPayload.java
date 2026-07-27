package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Empty marker payload registered clientbound (S2C) so that
 * {@link net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking#canSend}
 * can be used server-side to detect whether a connected player has
 * WaylandCraft installed -- canSend only reflects channels the CLIENT has
 * registered to receive, which for a serverbound-only payload (like
 * ServerboundGiveItemsPayload) is never true regardless of mod presence.
 */
public record ClientboundHelloPayload() implements CustomPacketPayload {

	public static final Identifier HELLO_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "hello");

	public static final CustomPacketPayload.Type<ClientboundHelloPayload> TYPE = new CustomPacketPayload.Type<ClientboundHelloPayload>(HELLO_PAYLOAD_ID);

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundHelloPayload> CODEC = StreamCodec.unit(new ClientboundHelloPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

}
