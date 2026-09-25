package dev.evvie.waylandcraft.sharing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.item.WindowHandle;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.sharing.SharingNetworking.AudioChunkPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.DemandPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.DisplayPosePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.FrameInfo;
import dev.evvie.waylandcraft.sharing.SharingNetworking.KeyFrameRequestPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.ShareStatePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamAudioPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamEndPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamPosePayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.StreamVideoPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.VideoChunkPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WatchPayload;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowKey;
import dev.evvie.waylandcraft.sharing.SharingNetworking.WindowPose;
import dev.evvie.waylandcraft.utils.IMyServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/* Relays shared window streams. The server never decodes anything: it tracks which
 * windows are shared and where they are shown (item frames, found by scanning, and
 * free-floating displays, reported by the owner), which viewers may receive video,
 * and who is close enough to hear them.
 *
 * Viewers report what they can see, but the server re-checks distance and line of sight
 * itself, so a modified client can't pull streams it couldn't see. Video is additionally
 * limited per viewer by a byte budget; frames over budget are dropped whole.
 */
public class SharingServer {

	// Players within this distance of a shared window hear its audio
	public static final double AUDIO_RANGE = 24.0;
	// Viewers further away than this never receive video
	public static final double WATCH_RANGE = 64.0;
	// Players within this distance receive floating window poses
	private static final double POSE_RANGE = 128.0;

	// Per-viewer video budget: sustained rate and burst, in bytes
	private static final double VIDEO_BYTES_PER_SECOND = 2_000_000;
	private static final double VIDEO_BURST_BYTES = 4_000_000;

	private static final int SCAN_INTERVAL = 10;

	private final Set<WindowKey> shared = new HashSet<>();
	// What each viewer asked for, and the subset that passed the server's checks
	private final Map<UUID, Set<WindowKey>> requested = new HashMap<>();
	private final Map<WindowKey, Set<UUID>> watchers = new HashMap<>();
	// Where shared windows are shown: item frames and floating displays
	private final Map<WindowKey, List<Placement>> placements = new HashMap<>();
	private final Map<WindowKey, Placement> floating = new HashMap<>();
	private final Map<WindowKey, Demand> lastDemand = new HashMap<>();
	private final Map<UUID, Budget> budgets = new HashMap<>();

	// Server-side fake shared windows for testing (/waylandcraft testpattern)
	private final Map<WindowKey, TestPatternSource> testPatterns = new HashMap<>();
	private long nextTestHandle = 1;

	private MinecraftServer server;
	private int tickCounter = 0;

	private static record Placement(ServerLevel level, Vec3 pos, WindowPose pose) {}
	private static record Demand(boolean video, boolean audio) {}

	private static class Budget {
		double bytes = VIDEO_BURST_BYTES;
		long lastRefill = System.nanoTime();
		// Last frame admitted per window; later chunks of other frames are dropped
		final Map<WindowKey, Integer> admittedFrame = new HashMap<>();
		// Windows whose H.264 chain this viewer can't decode until the next key frame
		final Set<WindowKey> waitingForKey = new HashSet<>();

		// Decided on a frame's first chunk, so viewers get whole frames or nothing
		boolean admit(WindowKey key, FrameInfo info, int frameBytes) {
			if(info.kind() == SharingNetworking.FRAME_DELTA && waitingForKey.contains(key)) return false;

			long now = System.nanoTime();
			bytes = Math.min(VIDEO_BURST_BYTES, bytes + (now - lastRefill) / 1e9 * VIDEO_BYTES_PER_SECOND);
			lastRefill = now;
			if(bytes < frameBytes) {
				// Skipping a key or delta frame breaks the chain; stills are independent
				if(info.kind() != SharingNetworking.FRAME_STILL) waitingForKey.add(key);
				return false;
			}

			bytes -= frameBytes;
			admittedFrame.put(key, info.frame());
			if(info.kind() == SharingNetworking.FRAME_KEY) waitingForKey.remove(key);
			return true;
		}
	}

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
		refresh();
	}

	public void onDisplayPose(ServerPlayer owner, DisplayPosePayload payload) {
		WindowKey key = new WindowKey(owner.getUUID(), payload.handle());
		if(!shared.contains(key)) return;

		WindowPose pose = payload.pose();

		// Floating displays can only be placed near their owner
		if(pose != null && pose.pivot().distanceTo(owner.getEyePosition()) > WATCH_RANGE) pose = null;

		setFloating(key, owner.serverLevel(), pose);
	}

	// Places (or with a null pose, removes) a shared window's floating display and tells nearby players
	void setFloating(WindowKey key, ServerLevel ownerLevel, @Nullable WindowPose pose) {
		Placement previous = floating.get(key);
		if(pose == null) floating.remove(key);
		else floating.put(key, new Placement(ownerLevel, pose.pivot(), pose));

		ServerLevel level = pose != null ? ownerLevel : (previous != null ? previous.level() : ownerLevel);
		Vec3 center = pose != null ? pose.pivot() : (previous != null ? previous.pos() : Vec3.ZERO);
		if(pose == null && previous == null) return;
		StreamPosePayload relay = new StreamPosePayload(key, pose);
		for(ServerPlayer player : level.players()) {
			if(player.getUUID().equals(key.owner())) continue;
			if(player.position().closerThan(center, POSE_RANGE)) send(player, relay);
		}
	}

	public void onWatch(ServerPlayer viewer, WatchPayload payload) {
		server = viewer.server;
		requested.put(viewer.getUUID(), new HashSet<>(payload.windows()));
		refresh();
	}

	public void onVideoChunk(ServerPlayer owner, VideoChunkPayload payload) {
		relayVideo(new WindowKey(owner.getUUID(), payload.handle()), payload.info(), payload.data());
	}

	void relayVideo(WindowKey key, FrameInfo info, byte[] data) {
		if(!shared.contains(key) || !info.valid()) return;

		StreamVideoPayload relay = new StreamVideoPayload(key, info, data);
		for(UUID viewerId : watchers.getOrDefault(key, Set.of())) {
			ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
			if(viewer == null) continue;

			Budget budget = budgets.computeIfAbsent(viewerId, (id) -> new Budget());
			if(info.index() == 0) {
				if(!budget.admit(key, info, data.length * info.count())) continue;
			}
			else if(!Integer.valueOf(info.frame()).equals(budget.admittedFrame.get(key))) {
				continue;
			}
			send(viewer, relay);
		}
	}

	public void onAudioChunk(ServerPlayer owner, AudioChunkPayload payload) {
		relayAudio(new WindowKey(owner.getUUID(), payload.handle()), payload.timestamp(), payload.opus());
	}

	void relayAudio(WindowKey key, long timestamp, byte[] opus) {
		if(!shared.contains(key)) return;

		StreamAudioPayload relay = new StreamAudioPayload(key, timestamp, opus);
		for(ServerPlayer listener : audioListeners(key)) {
			send(listener, relay);
		}
	}

	public void onLogout(ServerPlayer player) {
		UUID id = player.getUUID();
		requested.remove(id);
		budgets.remove(id);
		for(WindowKey key : new ArrayList<>(shared)) {
			if(key.owner().equals(id)) endStream(key);
		}
		refresh();
	}

	// Called every server level tick
	public void tick(ServerLevel level) {
		server = level.getServer();
		if(level != server.overworld()) return;

		for(TestPatternSource source : testPatterns.values()) source.tick(this);

		if(++tickCounter % SCAN_INTERVAL != 0) return;

		// Test patterns resend their placement, like owners do, for players who came into range
		if(tickCounter % 40 == 0) {
			for(TestPatternSource source : testPatterns.values()) setFloating(source.key, source.level, source.pose);
		}

		// Drop shares whose window closed on the owner's side
		for(WindowKey key : new ArrayList<>(shared)) {
			if(testPatterns.containsKey(key)) continue;
			ServerPlayer owner = server.getPlayerList().getPlayer(key.owner());
			if(owner == null || !((IMyServerPlayer) owner).getAliveWindows().contains(key.handle())) endStream(key);
		}

		scanFrames();
		refresh();
	}

	// Finds item frames showing shared windows, across all levels, and adds floating displays
	private void scanFrames() {
		placements.clear();
		if(shared.isEmpty()) return;

		for(ServerLevel level : server.getAllLevels()) {
			for(Entity entity : level.getAllEntities()) {
				if(!(entity instanceof ItemFrame frame)) continue;
				if(!frame.getItem().is(WindowItem.WINDOW)) continue;

				WindowHandle handle = frame.getItem().get(WindowItem.WINDOW_HANDLE);
				if(handle == null) continue;

				WindowKey key = new WindowKey(handle.player(), handle.handle());
				if(!shared.contains(key)) continue;

				Vec3 facing = Vec3.atLowerCornerOf(frame.getDirection().getNormal());
				placements.computeIfAbsent(key, (k) -> new ArrayList<>()).add(new Placement(level, frame.getBoundingBox().getCenter().add(facing.scale(0.1)), null));
			}
		}

		floating.forEach((key, placement) -> placements.computeIfAbsent(key, (k) -> new ArrayList<>()).add(placement));
	}

	// Recomputes validated watchers and tells owners when watching / listening starts or stops
	private void refresh() {
		if(server == null) return;

		Map<WindowKey, Set<UUID>> previous = new HashMap<>(watchers);
		watchers.clear();
		for(Map.Entry<UUID, Set<WindowKey>> entry : requested.entrySet()) {
			ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey());
			if(viewer == null) continue;

			for(WindowKey key : entry.getValue()) {
				if(!shared.contains(key) || key.owner().equals(viewer.getUUID())) continue;
				if(canSee(viewer, key)) watchers.computeIfAbsent(key, (k) -> new HashSet<>()).add(viewer.getUUID());
			}
		}

		// New watchers can only decode from a key frame: hold their deltas and ask the owner for one
		for(Map.Entry<WindowKey, Set<UUID>> entry : watchers.entrySet()) {
			boolean joined = false;
			for(UUID viewer : entry.getValue()) {
				if(previous.getOrDefault(entry.getKey(), Set.of()).contains(viewer)) continue;
				budgets.computeIfAbsent(viewer, (id) -> new Budget()).waitingForKey.add(entry.getKey());
				joined = true;
			}

			TestPatternSource testPattern = testPatterns.get(entry.getKey());
			if(joined && testPattern != null) testPattern.requestKeyFrame();

			ServerPlayer owner = joined ? server.getPlayerList().getPlayer(entry.getKey().owner()) : null;
			if(owner != null) send(owner, new KeyFrameRequestPayload(entry.getKey().handle()));
		}

		for(WindowKey key : shared) {
			Demand demand = new Demand(watchers.containsKey(key), !audioListeners(key).isEmpty());
			if(demand.equals(lastDemand.get(key))) continue;
			lastDemand.put(key, demand);

			ServerPlayer owner = server.getPlayerList().getPlayer(key.owner());
			if(owner != null) send(owner, new DemandPayload(key.handle(), demand.video(), demand.audio()));
		}
	}

	// Server-side check of a viewer's claim: in range of a placement, with no blocks in between
	private boolean canSee(ServerPlayer viewer, WindowKey key) {
		Vec3 eye = viewer.getEyePosition();
		for(Placement placement : placements.getOrDefault(key, List.of())) {
			if(placement.level() != viewer.serverLevel()) continue;
			if(!placement.pos().closerThan(eye, WATCH_RANGE)) continue;

			HitResult hit = placement.level().clip(new ClipContext(eye, placement.pos(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
			if(hit.getType() == HitResult.Type.MISS) return true;
		}
		return false;
	}

	// Audio ignores line of sight: everyone near a placement of the window hears it
	private List<ServerPlayer> audioListeners(WindowKey key) {
		List<ServerPlayer> listeners = new ArrayList<>();
		for(Placement placement : placements.getOrDefault(key, List.of())) {
			for(ServerPlayer player : placement.level().players()) {
				if(player.getUUID().equals(key.owner())) continue;
				if(listeners.contains(player)) continue;
				if(player.position().closerThan(placement.pos(), AUDIO_RANGE)) listeners.add(player);
			}
		}
		return listeners;
	}

	boolean hasVideoDemand(WindowKey key) {
		return watchers.containsKey(key);
	}

	boolean hasAudioDemand(WindowKey key) {
		return !audioListeners(key).isEmpty();
	}

	/* Starts a test pattern window floating in front of the player. Returns its window
	 * handle (owned by TestPatternSource.OWNER), so the player can also get an item for it.
	 */
	public long startTestPattern(ServerPlayer player) {
		server = player.server;
		long handle = nextTestHandle++;

		// Face the player, upright, about 2.5 blocks in front of their eyes
		Vec3 look = player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0, look.z);
		horizontal = horizontal.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : horizontal.normalize();
		WindowPose pose = new WindowPose(player.getEyePosition().add(horizontal.scale(2.5)), horizontal.reverse(), new Vec3(0, -1, 0), 1.6f, 1.2f);

		TestPatternSource source = new TestPatternSource(handle, player.serverLevel(), pose);
		testPatterns.put(source.key, source);
		shared.add(source.key);
		setFloating(source.key, source.level, pose);
		scanFrames();
		refresh();
		return handle;
	}

	public int stopTestPatterns() {
		int count = testPatterns.size();
		for(WindowKey key : new ArrayList<>(testPatterns.keySet())) {
			setFloating(key, testPatterns.get(key).level, null);
			testPatterns.remove(key);
			endStream(key);
		}
		return count;
	}

	public boolean isTestPattern(WindowHandle handle) {
		return handle != null && testPatterns.containsKey(new WindowKey(handle.player(), handle.handle()));
	}

	// Players without the mod (or with an older version) don't have the channel
	private static void send(ServerPlayer player, CustomPacketPayload payload) {
		if(player.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(player, payload);
	}

	private void endStream(WindowKey key) {
		if(!shared.remove(key)) return;
		lastDemand.remove(key);
		placements.remove(key);
		floating.remove(key);
		watchers.remove(key);
		if(server == null) return;

		StreamEndPayload end = new StreamEndPayload(key);
		for(ServerPlayer player : server.getPlayerList().getPlayers()) {
			if(!player.getUUID().equals(key.owner())) send(player, end);
		}

		ServerPlayer owner = server.getPlayerList().getPlayer(key.owner());
		if(owner != null) send(owner, new DemandPayload(key.handle(), false, false));
	}

}
