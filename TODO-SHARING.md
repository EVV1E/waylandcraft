# Read-only window sharing (prototype)

Branch: `window-sharing` (based on `neoforge-1.21.1`).

Goal: other players can **watch and hear** a window someone chooses to share. The target
use cases are **video**, like a browser playing YouTube, and **terminals**. A shared window
shows up wherever its owner shows it: in an item frame, or as a free-floating in-world
window. It's read-only: input only ever goes to the owner's local compositor, and this
adds no input path.

## How it works

```
owner client                         server                           viewer clients
------------                         ------                           --------------
[N] toggles sharing of the
focused window  --ShareState-->   tracks shared windows and where
                                  they are shown:
                --DisplayPose-->  - floating displays (owner reports)   --StreamPose-->  render floating
                                  - item frames (scanned, 10 ticks)                      windows in-world
                                                         <--Watch---  shared windows in view and in
                                                                      line of sight (every 5 ticks)
                                  re-checks each claim itself
                                  (distance + block raycast)
                <--Demand-------  video: any validated watcher?
                                  audio: anyone within 24 blocks?
video, only while watched AND the window changed:
  motion: JPEG <=854 px, <=15 fps
  still:  one PNG <=1600 px after 600 ms unchanged
  --VideoChunk-->                 relays to validated watchers,     --StreamVideo-->  reassemble, decode,
  (30 KB chunks)                  within each viewer's byte budget                    DynamicTexture
audio, only while someone is near:
  pw-record of the app's own
  PipeWire stream -> Opus 64 kb/s
  --AudioChunk-->                 relays to players in range        --StreamAudio-->  Opus decode, OpenAL
  (one 20 ms packet each)         (no line of sight needed)                          source at the window
```

### Video for both movies and terminals

- **Change-driven:** the window framebuffer counts content changes: surface damage from
  the compositor, or a resize. Capture and upload happen only when that count moves, so
  an idle terminal costs nothing at all.
- **Motion:** while a window keeps changing (a playing video, scrolling output), frames go
  out as JPEG at up to 854 px (about 480p) and up to 15 fps.
- **Still refinement:** once the window has been unchanged for 600 ms, one lossless PNG at
  up to 1600 px follows. Terminal text becomes sharp after typing stops, and a paused
  video shows a clean frame. Terminal PNGs compress very well because of their flat colors.
- **Newcomers:** when someone starts watching, the owner resends a fresh frame.

### Smart rendering: only stream what someone can see

- **Viewer check:** viewers compute which shared windows are within 64 blocks, inside the
  camera frustum and in line of sight (no blocks between the eye and the window). They
  report that set, only when it changes.
- **Server re-check:** the server repeats the distance and line-of-sight check against the
  positions it knows. A modified client can't pull streams it couldn't see.
- **Owner demand:** owners capture video only while at least one validated watcher exists.
- **Audio ignores line of sight:** anyone within 24 blocks hears it, positionally from the
  window. You hear the music around the corner, but don't receive its video.
- **Per-viewer budget:** the server limits video per viewer to 2 MB/s sustained with a
  4 MB burst. Frames over budget are dropped whole: admission is decided on a frame's first
  chunk, so viewers never see half frames.

### Audio details

Wayland doesn't carry audio: applications talk to PipeWire directly. So audio is matched
per window, not routed through the compositor:

1. **Find the process.**
   - Native Wayland clients: the bridge returns the client's pid through `toplevelPID`,
     a new JNI function that leaves existing signatures untouched.
   - X11 apps: the Wayland client is xwayland-satellite, so the app's pid comes from the
     X server instead. That's the `_NET_WM_PID` of the managed window
     (`_NET_CLIENT_LIST`) whose title matches, found with `xprop`.
2. **Find the stream.** `pw-dump` finds a `Stream/Output/Audio` node owned by that process
   or one of its descendants (browsers play audio from child processes). The pid is on
   the node for PulseAudio clients, and on the linked client object (`client.id`) for
   native PipeWire clients.
3. **Record only that stream** with `pw-record --raw --target <serial>`. It runs with
   `node.dont-fallback` and `node.dont-reconnect`, so it can never fall back to the
   microphone. Verified: with a missing target it refuses to record.
4. **Encode** to Opus: 48 kHz mono at 64 kb/s, about 8 KB/s. This uses Concentus, a
   pure-Java Opus port bundled as jar-in-jar, so viewers on any OS can decode it.
   Playback is a positional OpenAL source, scaled by the Master and Jukebox/Note Blocks
   volume.
5. The lookup runs on a background thread. The application still plays normally for its
   owner, and the owner never plays their own stream back.

## Using it

1. **Server:** needs this build of the mod. Singleplayer opened to LAN works too.
2. **Owner:**
   - Focus a window and press **N** (Toggle Window Sharing). A chat message confirms it,
     and a red "● Sharing N windows" indicator stays on the HUD.
   - Show it to others either by putting its window item in an item frame, or by placing
     it in the world as usual (right-click with the window item).
3. **Viewers:** look at it. Video starts immediately, and audio plays within 24 blocks.
4. **To stop:** press **N** on the focused window again, close the window, or log out.

Testing on one machine:
1. `./gradlew runServer`. Accept the EULA in `run-server/eula.txt` first.
2. `./gradlew runClient`
3. `./gradlew runClient2`, which joins as `Viewer` from `run-client2/`.

## Status

**Verified:**
- It compiles, and the dev client starts with sharing registered. It also exits cleanly
  on SIGTERM; a shutdown-hook race found here was fixed on `neoforge-1.21.1` too.
- The `pw-record` invocation captures exactly one app's stream.
- The PipeWire lookup works for native PipeWire clients.
- Opus round trip through Concentus: 8.1 KB/s, with the tone's amplitude kept to within
  about 1.4%.

**Not yet tested end-to-end:** two players watching a shared browser or terminal, the
server's validation and budget under load, the X11 pid lookup (it depends on
xwayland-satellite publishing `_NET_CLIENT_LIST`), and Iris with shared windows.

## Known limitations / next steps

- **Codec:**
  - Motion JPEG has no inter-frame compression, so a 480p video watcher costs roughly
    0.5–1.5 MB/s. A real video codec (VP8/H.264 through the native library) would cut
    this about 10x, but needs a native decoder on every viewer.
  - Sending only damaged regions would help terminals most while typing.
- **A/V sync:** audio and video aren't synchronized. Video latency is roughly
  capture + encode + one frame interval (about 100 ms); audio buffers about 100 ms before
  starting. There are no timestamps yet.
- **Transparency:** frames are flattened to opaque, so translucent terminal backgrounds
  show as their color over black.
- **Capture:** `glReadPixels` is synchronous. A PBO would make it asynchronous at higher
  resolutions and frame rates.
- **Audio:**
  - Mono only: stereo sources can't be positional in OpenAL.
  - Playback isn't paused with the game.
  - Only one stream per app is captured, the first one found.
- **Sharing controls:** audio and video are shared together; there's no separate toggle yet.
- **Placement:** the server trusts the owner's placement within 64 blocks of the owner.
  Windows can't be placed far away, but they aren't bound to the owner's own display.
- **Licensing:** Concentus is a libopus port under a BSD-style license (like libopus).
  Confirm the exact license file before any release.
