package dev.evvie.waylandcraft.network;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundGiveItemsPayload(long[] handles, boolean missingOnly) implements CustomPacketPayload {
	
	public static final ResourceLocation GIVE_ITEMS_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "give_items");
	
	public static final CustomPacketPayload.Type<ServerboundGiveItemsPayload> TYPE = new CustomPacketPayload.Type<ServerboundGiveItemsPayload>(GIVE_ITEMS_PAYLOAD_ID);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundGiveItemsPayload> CODEC = StreamCodec.composite(WaylandCraftNetworking.LONG_ARRAY, ServerboundGiveItemsPayload::handles, ByteBufCodecs.BOOL, ServerboundGiveItemsPayload::missingOnly, ServerboundGiveItemsPayload::new);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
}
