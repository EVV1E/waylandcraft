package dev.evvie.waylandcraft.sharing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.sharing.SharingNetworking.AudioChunkPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.DemandPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.ShareStatePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamAudioPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamEndPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamVideoPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.VideoChunkPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WatchPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowKey;
import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/* Relays shared window streams. The server never decodes anything: it tracks which
 * windows are shared, who is watching them (viewers report windows they can see), and
 * who is close enough to hear them, and forwards chunks accordingly. Owners are told
 * whether there is any demand so they only capture and upload when someone benefits.
 */
public class SharingServer {

	// Players within this distance of a frame showing a shared window hear its audio
	public static final double AUDIO_RANGE = 24.0;

	private static final int FRAME_SCAN_INTERVAL = 10;

	private final Set<WindowKey> shared = new HashSet<>();
	private final Map<UUID, Set<WindowKey>> watching = new HashMap<>();
	private final Map<WindowKey, List<FramePos>> frames = new HashMap<>();
	private final Map<WindowKey, Demand> lastDemand = new HashMap<>();

	private MinecraftServer server;
	private int tickCounter = 0;

	private static record FramePos(ServerLevel level, Vec3 pos) {}
	private static record Demand(boolean video, boolean audio) {}

	public void onShareState(ServerPlayer owner, ShareStatePayload payload) {
		server = owner.server;
		WindowKey key = new WindowKey(owner.getUUID(), payload.handle());

		if(payload.shared()) {
			// Only windows the owner's client reported as alive can be shared
			if(!((IMyServerPlayer) owner).getAliveWindows().contains(payload.handle())) return;
			shared.add(key);
		}
		else {
			endStream(key);
		}
		updateDemand();
	}

	public void onWatch(ServerPlayer viewer, WatchPayload payload) {
		server = viewer.server;
		watching.put(viewer.getUUID(), new HashSet<>(payload.windows()));
		updateDemand();
	}

	public void onVideoChunk(ServerPlayer owner, VideoChunkPayload payload) {
		WindowKey key = new WindowKey(owner.getUUID(), payload.handle());
		if(!shared.contains(key)) return;
		if(payload.count() < 1 || payload.count() > SharingNetworking.MAX_CHUNKS_PER_FRAME) return;

		StreamVideoPayload relay = new StreamVideoPayload(key, payload.frame(), payload.index(), payload.count(), payload.data());
		for(ServerPlayer viewer : videoViewers(key)) {
			send(viewer, relay);
		}
	}

	public void onAudioChunk(ServerPlayer owner, AudioChunkPayload payload) {
		WindowKey key = new WindowKey(owner.getUUID(), payload.handle());
		if(!shared.contains(key)) return;

		StreamAudioPayload relay = new StreamAudioPayload(key, payload.pcm());
		for(ServerPlayer listener : audioListeners(key)) {
			send(listener, relay);
		}
	}

	public void onLogout(ServerPlayer player) {
		UUID id = player.getUUID();
		watching.remove(id);
		for(WindowKey key : new ArrayList<>(shared)) {
			if(key.owner().equals(id)) endStream(key);
		}
		updateDemand();
	}

	// Called every server level tick
	public void tick(ServerLevel level) {
		server = level.getServer();
		if(level != server.overworld()) return;
		if(++tickCounter % FRAME_SCAN_INTERVAL != 0) return;

		// Drop shares whose window closed on the owner's side
		shared.removeIf((key) -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(key.owner());
			return owner == null || !((IMyServerPlayer) owner).getAliveWindows().contains(key.handle());
		});

		scanFrames();
		updateDemand();
	}

	// Finds item frames showing shared windows, across all levels
	private void scanFrames() {
		frames.clear();
		if(shared.isEmpty()) return;

		for(ServerLevel level : server.getAllLevels()) {
			for(Entity entity : level.getAllEntities()) {
				if(!(entity instanceof ItemFrame frame)) continue;
				if(!frame.getItem().is(WindowItem.WINDOW)) continue;

				WindowHandle handle = frame.getItem().get(WindowItem.WINDOW_HANDLE);
				if(handle == null) continue;

				WindowKey key = new WindowKey(handle.player(), handle.handle());
				if(!shared.contains(key)) continue;

				frames.computeIfAbsent(key, (k) -> new ArrayList<>()).add(new FramePos(level, frame.position()));
			}
		}
	}

	private List<ServerPlayer> videoViewers(WindowKey key) {
		List<ServerPlayer> viewers = new ArrayList<>();
		if(server == null) return viewers;

		for(Map.Entry<UUID, Set<WindowKey>> entry : watching.entrySet()) {
			if(entry.getKey().equals(key.owner())) continue;
			if(!entry.getValue().contains(key)) continue;

			ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey());
			if(viewer != null) viewers.add(viewer);
		}
		return viewers;
	}

	// Audio ignores line of sight: everyone near a frame showing the window hears it
	private List<ServerPlayer> audioListeners(WindowKey key) {
		List<ServerPlayer> listeners = new ArrayList<>();
		for(FramePos frame : frames.getOrDefault(key, List.of())) {
			for(ServerPlayer player : frame.level().players()) {
				if(player.getUUID().equals(key.owner())) continue;
				if(listeners.contains(player)) continue;
				if(player.position().closerThan(frame.pos(), AUDIO_RANGE)) listeners.add(player);
			}
		}
		return listeners;
	}

	// Tells owners when watching / listening starts or stops
	private void updateDemand() {
		if(server == null) return;

		for(WindowKey key : shared) {
			Demand demand = new Demand(!videoViewers(key).isEmpty(), !audioListeners(key).isEmpty());
			if(demand.equals(lastDemand.get(key))) continue;
			lastDemand.put(key, demand);

			ServerPlayer owner = server.getPlayerList().getPlayer(key.owner());
			if(owner != null) send(owner, new DemandPayload(key.handle(), demand.video(), demand.audio()));
		}
	}

	// Players without the mod (or with an older version) don't have the channel
	private static void send(ServerPlayer player, CustomPacketPayload payload) {
		if(player.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(player, payload);
	}
	
	private void endStream(WindowKey key) {
		if(!shared.remove(key)) return;
		lastDemand.remove(key);
		frames.remove(key);
		if(server == null) return;

		StreamEndPayload end = new StreamEndPayload(key);
		for(ServerPlayer player : server.getPlayerList().getPlayers()) {
			if(!player.getUUID().equals(key.owner())) send(player, end);
		}

		ServerPlayer owner = server.getPlayerList().getPlayer(key.owner());
		if(owner != null) send(owner, new DemandPayload(key.handle(), false, false));
	}

}
