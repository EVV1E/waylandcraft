# Read-only window sharing (prototype)

Branch: `window-sharing` (based on `neoforge-1.21.1`).

Goal: other players can **watch and hear** a window someone chooses to share. The target
use cases are **video**, like a browser playing YouTube, and **terminals**. A shared window
shows up wherever its owner shows it: in an item frame, or as a free-floating in-world
window. It's read-only: input only ever goes to the owner's local compositor, and this
adds no input path.

**Viewers can be on any platform.** Decoding (H.264 through JCodec, Opus through
Concentus) is pure Java and bundled in the jar, and the viewer hooks are registered even
in non-Linux fallback mode. Sharing a window needs the Linux compositor.

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
                <--KeyFrameReq--  a new watcher joined
video, only while watched AND the window changed:
  motion: H.264 <=854 px, <=15 fps,
          key frame every 30 frames
  still:  one PNG <=1600 px after 600 ms unchanged
  --VideoChunk-->                 relays to validated watchers,     --StreamVideo-->  reassemble, decode,
  (30 KB chunks, kind + size +    within each viewer's byte budget;                  hold until the audio
   capture timestamp)             after a drop, deltas wait for a                    clock reaches the
                                  key frame                                          frame, then show
audio, only while someone is near:
  pw-record of the app's own
  PipeWire stream -> stereo Opus
  96 kb/s, timestamped
  --AudioChunk-->                 relays to players in range        --StreamAudio-->  Opus decode, two OpenAL
  (one 20 ms packet each)         (no line of sight needed)                          sources at the window's
                                                                                      left and right edges
```

### Video codec (H.264: x264 on the owner, JCodec on viewers)

**Owner:** encodes with **x264**, linked into the native library (`native/src/h264.rs`,
`NativeH264Encoder`).
- **Settings:** baseline profile, zero-latency tuning, constant QP 30, no B-frames, no
  scene cuts. Key frames come only on request, and SPS/PPS repeat on every key frame.
- **Measured at 320×240 test content**, end to end through the real Java and native code
  into the JCodec decoder:

  | | x264 | JCodec encoder |
  |---|---|---|
  | Key frame | about 1.9 KB | 3.6 KB |
  | Delta frame | 100–220 bytes | 2–3 KB |
  | Mean color error | 0.8/255 | about 8/255 |

  A late viewer's fresh decoder starts cleanly at an on-demand key frame.
- **Measured at 848×480 through the `x264` CLI with the same settings:** JCodec decoded
  all 60 frames of each clip.

  | Content | x264, per frame after the first | JCodec encoder |
  |---|---|---|
  | Panning video | 1.5 KB | 24 KB |
  | Scrolling terminal | 8.7 KB | 20 KB |
  | Typing in a terminal | 1.8 KB | 8 KB |

- **Fallback:** if the native encoder can't be used (for example, an older native
  library), the owner falls back to JCodec's encoder for the rest of the session.

**Linking:**
- **Dev builds** link the system `libx264` dynamically.
- **Release builds** link a static, position-independent x264. It's built from source in
  the workflow, and in the Ubuntu 22.04 container with
  `SYSTEM_DEPS_X264_LINK=static PKG_CONFIG_PATH=<x264>/lib/pkgconfig`.
  Verified: that library has no `libx264` runtime dependency, needs glibc 2.34, and passes
  the same round trip.
- **License:** x264 is GPLv2+, compatible with the mod's GPLv3.
- **Build requirements:** bindgen, so `libclang` at build time, plus `nasm` for x264.

**Viewers:** decode with **JCodec** (pure Java). JCodec's own encoder measured at
848×480 on this machine:

| Content | Earlier motion JPEG | H.264, QP 30 |
|---|---|---|
| Terminal, typing | 116 KB per frame | 8 KB per changed frame |
| Terminal, scrolling | 120 KB per frame | 20 KB per frame |
| Panning video | 42 KB per frame | 24 KB per frame (key frame 31 KB) |

- **Speed:** encoding takes 15–25 ms per frame and decoding about 13 ms, each on a
  background thread.
- **Color:** a key-plus-delta round trip through `H264Codec` and `NativeImage` keeps
  colors within about 8/255 mean error, and 34 at worst on saturated colors (4:2:0 chroma).
- **Key frames** go out every 30 motion frames, whenever the size changes, whenever a new
  watcher joins (the server sends `KeyFrameRequest`), and after the owner had to drop an
  oversized frame. Key frames carry SPS/PPS, so a late viewer's fresh decoder starts
  cleanly at any of them (tested).
- **Delta frames** depend on everything since the last key frame, so the chain is protected
  at every step:
  - The server drops whole frames over a viewer's budget. After dropping a key or delta
    frame, it holds that viewer's deltas until the next key frame, and new watchers start
    in that state too.
  - Viewers also skip deltas after any incomplete or undecodable frame, until the next key
    frame.
- **Change-driven:** capture only happens when the window's content changed (surface
  damage or a resize), so an idle terminal costs nothing.
- **Still refinement:** once the window has been unchanged for 600 ms, one lossless PNG at
  up to 1600 px follows, so terminal text is sharp once typing stops.

### A/V sync

- Video frames and audio packets carry the owner's capture time, from the same clock on
  the owner's machine.
- The audio player keeps a playback clock: the capture time of the sample being heard,
  from its queued packet timestamps plus OpenAL's sample offset.
- Decoded frames wait in a small queue, and each render frame shows the newest one the
  audio clock has reached.
- Without audio, or if the clocks are over 1.5 s apart, frames show as soon as they're
  decoded.

### Stereo

OpenAL only spatializes mono sources. So the left and right channels play from two mono
sources placed at the window's left and right edges:
- floating windows: the window's real width,
- item frames: ±0.4 blocks,
- frames on floors and ceilings: both sources at the center.

In front of the screen you hear stereo across it; from further away it blends into one
positional sound. Both sources get the same buffers and start together through
`alSourcePlayv`, so they stay in step.

### Smart rendering: only stream what someone can see

- **Viewer check:** viewers compute which shared windows are within 64 blocks, inside the
  camera frustum and in line of sight (no blocks between the eye and the window). They
  report that set, only when it changes.
- **Server re-check:** the server repeats the distance and line-of-sight check against the
  positions it knows, so a modified client can't pull streams it couldn't see.
- **Owner demand:** owners capture video only while at least one validated watcher exists.
- **Audio ignores line of sight:** anyone within 24 blocks hears it, positionally from the
  window.
- **Per-viewer budget:** video is capped at 2 MB/s sustained with a 4 MB burst per viewer.

### Audio capture

Wayland doesn't carry audio: applications talk to PipeWire directly. So audio is matched
per window:

1. **Find the process.**
   - Native Wayland clients: the bridge returns the client's pid through `toplevelPID`, a
     new JNI function.
   - X11 apps: the Wayland client is xwayland-satellite, so the pid comes from the X server
     instead. That's the `_NET_WM_PID` of the managed window whose title matches, found
     with `xprop`.
2. **Find the stream.** `pw-dump` finds a `Stream/Output/Audio` node owned by that process
   or a descendant. The pid is on the node for PulseAudio clients, and on the linked client
   object for native PipeWire clients.
3. **Record only that stream** with `pw-record --raw --target <serial>`, using
   `node.dont-fallback` and `node.dont-reconnect`. It never falls back to the microphone
   (verified).
4. **Encode** as stereo Opus at 96 kb/s. The lookup runs on a background thread, and the
   app still plays normally for its owner.

## Using it

1. **Server:** needs this build of the mod. Singleplayer opened to LAN works too.
2. **Owner:**
   - Focus a window and press **N** (Toggle Window Sharing). A chat message confirms it,
     and a red "● Sharing N windows" indicator stays on the HUD.
   - Show it to others by putting its window item in an item frame, or by placing it in
     the world as usual (use the window item).
3. **Viewers:** look at it. Audio plays within 24 blocks.
4. **To stop:** press **N** on the focused window again, close the window, or log out.

Testing on one machine:
1. `./gradlew runServer`. Accept the EULA in `run-server/eula.txt` first.
2. `./gradlew runClient`
3. `./gradlew runClient2`, which joins as `Viewer`.

## Status

**Verified in isolation:**
- JCodec speed and size (the table above), late-join decoding at a mid-stream key frame,
  and odd frame sizes.
- `H264Codec` color round trip.
- Opus round trip.
- `pw-record` capturing exactly one app's stream, and the PipeWire stream lookup.
- The dev client starts and exits cleanly with everything registered.

**Not yet tested end-to-end:** two players, the server's validation and budget,
key-frame recovery after drops, A/V sync by ear, stereo placement, the X11 pid lookup,
a non-Linux viewer, and Iris.

## Known limitations / next steps

- **H.264:** everything is baseline, because the JCodec decoder needs it. Main profile
  (CABAC) would save roughly another 10–15%, if JCodec's CABAC decoding holds up in tests.
- **A/V sync:** it follows the audio clock only, and doesn't correct clock drift between
  owner and viewer beyond the 1.5 s fallback. That should be fine for sessions of normal
  length.
- **Transparency:** frames are flattened to opaque, so translucent terminal backgrounds
  show over black.
- **Capture:** `glReadPixels` is synchronous. A PBO would make it asynchronous.
- **Audio:** only one stream per app is captured, and playback isn't paused with the game.
- **Controls:** audio and video are shared together.
- **Placement:** floating windows are trusted within 64 blocks of their owner.
- **Licensing:** Concentus is a libopus port under a BSD-style license, JCodec is BSD
  (FreeBSD) licensed, and x264 is GPLv2+. Confirm the license files before any release.
