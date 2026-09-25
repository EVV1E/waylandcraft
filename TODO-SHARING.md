# Read-only window sharing (prototype)

Branch: `window-sharing` (based on `neoforge-1.21.1`).

Goal: other players can **see and hear** a window someone chooses to share, shown in
an item frame. Viewers can't interact with it: input only ever goes to the owner's
local compositor, and this adds no input path.

## How it works

```
owner client                         server                          viewer clients
------------                         ------                          --------------
[N] toggles sharing of the
focused window  --ShareState-->  tracks shared windows
                                 finds item frames holding them
                                 (scan every 10 ticks)
                                                        <--Watch--   frames with other players'
                                                                     windows that are in view and
                                                                     in line of sight (every 5 ticks)
                <--Demand------  video: anyone watching?
                                 audio: anyone within 24 blocks
                                        of a frame showing it?
video (only while watched):
  GPU downscale to <=480 px,
  read back, JPEG q0.7, <=4 fps,
  skipped if identical  --VideoChunk-->  relays only to     --StreamVideo-->  reassemble, decode,
  (split to 30 KB chunks)                watchers                             DynamicTexture, drawn
                                                                              in the item frame
audio (only while someone is near):
  pw-record of the app's PipeWire
  stream, 24 kHz mono s16,
  50 ms chunks          --AudioChunk-->  relays to players  --StreamAudio-->  OpenAL source at the
                                         near a frame                         frame (positional,
                                         (no line of sight                    Records volume)
                                          needed)
```

### Smart rendering (demand-driven)

- **Video is only captured, encoded and uploaded while at least one other player can
  actually see the window.** For each frame holding someone else's window, the viewer
  checks all of these:
  - it's within 64 blocks,
  - it's inside the camera frustum of the last rendered frame, and
  - a block raycast to a point just in front of the frame hits nothing.
- Viewers send only the set of windows they can see, and only when it changes. The
  server relays video chunks only to those viewers, and tells the owner when demand
  starts and stops.
- **Audio ignores line of sight.** It's captured while any other player is within 24
  blocks of a frame showing the window, and plays positionally at the frame. So you can
  hear a music player around the corner but won't receive its video.
- Idle cost is zero: a shared window nobody can see or hear is not captured at all.

### Audio details

Wayland doesn't carry audio: applications talk to PipeWire directly. So audio is
matched per window, not routed through the compositor:

1. The native bridge gets the window's client process id. `toplevelPID` is a new JNI
   function; it uses `wl_client` credentials and touches no existing signature.
2. `pw-dump` finds a `Stream/Output/Audio` node owned by that process or a descendant,
   since browsers play audio from child processes. The pid is on the node for
   PulseAudio clients and on the linked client object (`client.id`) for native
   PipeWire clients.
3. `pw-record --raw --target <serial>` records only that stream. It runs with
   `node.dont-fallback` and `node.dont-reconnect`, so it can never fall back to the
   microphone or another source if the stream disappears. This was verified: with a
   missing target it refuses to record.
4. The application still plays normally for the owner. The owner's own client never
   plays the shared stream back.

## Using it (prototype)

1. The server needs this build of the mod. Singleplayer opened to LAN works too.
2. **Owner:**
   - Focus a window and press **N** (Toggle Window Sharing). A chat message confirms it,
     and a red "● Sharing N windows" indicator stays on the HUD.
   - Put that window's item in an item frame.
3. **Viewers:** look at the frame. Video starts within a few frames, and audio starts
   when you're within 24 blocks.
4. Press **N** on the focused window again to stop. Closing the window or logging out
   also stops it.

Testing on one machine: `./gradlew runServer` (accept the EULA in `run-server/eula.txt`
first), then `./gradlew runClient` and `./gradlew runClient2`. The second client runs as
`Viewer` in `run-client2/`, so both can join `localhost`.

## Status

**Verified:**
- It compiles, and the dev client starts with the sharing payloads registered.
- The `pw-record` invocation captures exactly one app's stream, with correct levels.
- The PipeWire stream lookup works for native PipeWire clients.

**Not yet tested end-to-end:** two players, an item frame, and a shared window.
That needs the two-client setup above.

## Known limitations / next steps

- **Security:**
  - The server only checks that a shared handle is one of the owner's alive windows.
    It doesn't check that watchers are actually near a frame; a modified client could
    request any shared window's video. Validate watchers against the frame positions
    the server already knows.
  - Sharing is opt-in per window, but it covers audio and video together. Consider
    separate toggles, and a per-window indicator on the frame itself.
- **Bandwidth:** whole-frame JPEG at up to 4 fps is roughly 30–100 KB/s per watched
  window per viewer, and raw PCM audio is 48 KB/s. Next steps:
  - send only damaged regions (`WLCSurface` already tracks damage),
  - Opus for audio (the native library could encode it),
  - a per-player bandwidth cap on the server.
- **Capture:** `glReadPixels` is synchronous. It's fine for small frames at 4 fps; a PBO
  would make it asynchronous.
- **Transparency:** JPEG drops alpha, so transparent window regions show black.
- **X11 windows:** the pid is xwayland-satellite's, so audio isn't matched for X11 apps.
  It would need the X11 client's `_NET_WM_PID`.
- **Scope:** windows are only shared through item frames. Free-floating window displays
  exist only on the owner's client, so sharing them would need their position synced
  through the server.
- **Audio playback:** there's no jitter handling beyond a 3-buffer start threshold and
  a 10-buffer cap, and it isn't paused with the game.
- **Iris:** shared frames render with `RenderType.entityCutout`, so they should work
  with shader packs, but this is untested.
