package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Carries no data and is never actually sent — it exists purely so its
 * channel identifier shows up in the per-connection registered-channel list
 * that Fabric's networking handshake exchanges with every connecting client.
 *
 * Clients that have WaylandCraft installed register this channel during the
 * networking handshake, allowing the server to detect per-player WaylandCraft
 * presence via ServerPlayNetworking.canSend().
 */
public record ClientboundPresenceMarkerPayload() implements CustomPacketPayload {

	public static final Identifier PRESENCE_MARKER_PAYLOAD_ID = Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "presence_marker");

	public static final CustomPacketPayload.Type<ClientboundPresenceMarkerPayload> TYPE = new CustomPacketPayload.Type<ClientboundPresenceMarkerPayload>(PRESENCE_MARKER_PAYLOAD_ID);

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPresenceMarkerPayload> CODEC = StreamCodec.unit(new ClientboundPresenceMarkerPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

}
