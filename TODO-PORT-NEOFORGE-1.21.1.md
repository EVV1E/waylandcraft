# Porting WaylandCraft to NeoForge 1.21.1

Seed instructions for an AI coding agent (or a human) picking this up. Read
this whole file before touching code — the two problems below are not the
same size, and treating them as one task will waste time.

## Why this exists

Upstream (`EVV1E/waylandcraft`, this repo's `origin` remote) targets Fabric
+ Minecraft 26.1.2. The goal here is a working build for **NeoForge 1.21.1**
specifically, to slot into the `MinecraftModpackDrakonixTechPack` modpack
(`~/Git/MinecraftModpackDrakonixTechPack`), which is pinned to NeoForge
21.1.251 on Minecraft 1.21.1 and already includes Iris.

## The two problems (do them in this order)

### 1. The version gap (1.21.1 vs 26.1.2) — do this investigation FIRST

This is the real risk, not the loader swap. Minecraft's renderer has
changed substantially across that span. Concretely, `GlBackendMixin`,
`IGlTextureMixin`, and parts of `WaylandCraftBridge` hook
`com.mojang.blaze3d.opengl.GlDevice` / `GlBackend` / `GpuDeviceBackend` /
`RenderSystem` — Mojang's GPU-abstraction rendering rewrite. **Before
writing any NeoForge code, check whether 1.21.1's client jar even has
these classes** (decompile/inspect `client-1.21.1-*.jar`, or check
NeoForge's 1.21.1 MDK). If 1.21.1 predates this rewrite, those mixins have
no equivalent target and the window-rendering approach needs to be
redesigned against whatever the 1.21.1-era renderer looks like (likely
direct `GlStateManager`/immediate-style calls) — that's a real design
task, not a mechanical port. Do not assume; verify against the actual
1.21.1 mappings/jar before estimating further work.

Also check every other vanilla class referenced by the 11 mixins in
`src/main/java/dev/evvie/waylandcraft/mixin/` against 1.21.1 mappings
(`MouseHandlerMixin`, `KeyboardHandlerMixin`, `ItemInHandRendererMixin`,
`MinecraftMixin`, `GuiMixin`, `NativeImageMixin`, `ItemFrameRendererMixin`,
`ItemFrameRenderStateMixin`, `FramerateLimitTrackerMixin`,
`TitleScreenMixin`, `PauseScreenMixin`, `ServerPlayerMixin`). Some may not
exist yet in 1.21.1 (e.g. anything tied to the render rewrite), some may
have different method signatures.

### 2. Fabric → NeoForge (mechanical, do this once #1's scope is known)

- **Build system**: Fabric Loom → NeoForge's ModDevGradle. `gradle.properties`
  needs NeoForge/NeoForm versions for 1.21.1 (21.1.251) instead of
  `loader_version`/`loom_version`/`fabric_version`. Compatibility level in
  both mixin configs is `JAVA_25` — 1.21.1/NeoForge 21.x needs Java 21, so
  that drops to `JAVA_21` (or whatever NeoForge 1.21.1 actually requires).
- **Entrypoints**: `fabric.mod.json`'s `main`/`client` entrypoints
  (`WaylandCraftCommon`, `WaylandCraft`) → a NeoForge `@Mod` class + mod
  bus event listeners (`FMLClientSetupEvent` etc).
- **Access widener → access transformer**: `waylandcraft.classtweaker`
  (Fabric's format) → NeoForge's `.at` file (`accesstransformer.cfg`
  syntax). Same fields/methods, different file format - go through it
  entry by entry, don't try to auto-convert blindly since the syntaxes
  aren't 1:1 in capability.
- **Mixin registration**: mixin *configs* (`waylandcraft.mixins.json`,
  `waylandcraft.client.mixins.json`) are mostly loader-agnostic (SpongePowered
  Mixin works the same way), but how they're *declared to the loader*
  differs (`fabric.mod.json`'s `mixins` array → NeoForge's
  `neoforge.mods.toml` + `META-INF/neoforge.mods.toml` mixin config
  declaration, or a `MixinExtras`/`FML` connector depending on NeoForge
  version conventions at the time of porting - check current NeoForge docs,
  this has changed between NeoForge versions before).
- **Networking**: `ServerboundAliveWindowsPayload` (and any other payload
  classes under `network/`) use Fabric's networking API - port to
  NeoForge's `IPayloadHandler`/`PayloadRegistrar` registration.
- **Dependencies**: `fabric-api` is depended on for networking/rendering
  hooks - NeoForge doesn't need a fabric-api equivalent for most of this
  (it's built into NeoForge itself), but audit every fabric-api import to
  confirm there's a NeoForge-native replacement for each specific hook
  used, not just networking.
- **Iris**: already compat-aware (`IrisCompat.java`, `compileOnly` on
  Iris in `build.gradle`). The modpack pins Iris `1.8.14-beta.1+mc1.21.1`
  for NeoForge - use that exact version when wiring up the compileOnly
  dependency and verify `IrisCompat.java`'s assumptions still hold against
  it.

### 3. What should NOT need porting

The native Rust compositor (`native/`, built via `native/Cargo.toml`,
producing `libwaylandcraft.so`) talks to Java purely over JNI - it has no
Fabric or Minecraft-version dependency. It should build and work unchanged
**as long as the JNI-facing Java class/method signatures it calls into stay
identical** (see `native/src/bridge.rs`, `java_types.rs` for what it expects
from the Java side). If you rename/move `WaylandCraftBridge` or the
`bridge/` package during the port, update the JNI signatures on the Rust
side to match, or better, don't rename them.

## Suggested approach

1. Do the 1.21.1-renderer investigation above first and write down what you
   find (which mixin targets exist, which don't, what the replacement API
   looks like) before writing a line of NeoForge code. This determines
   whether the rest is a mechanical port or a partial redesign.
2. Get a minimal NeoForge 1.21.1 mod shell building and loading (empty
   `@Mod`, no mixins, no native lib) before porting anything else.
3. Port the access transformer and confirm the widened members are
   actually reachable.
4. Bring in the native lib + JNI bridge layer (`WaylandCraftBridge` and
   friends) without any mixins yet - confirm the compositor process starts
   and JNI calls round-trip.
5. Port mixins one at a time, in dependency order (input handling before
   rendering), testing after each one.
6. Port networking payloads.
7. Only then chase Iris compat and polish.

## Upstream contribution policy - read before opening any PR

Per `~/Git/waylandcraft/README.md`'s Contribution Policy: PRs made with
**major LLM usage must be disclosed** and filed as a **draft PR**, not a
normal mergeable one - the maintainer expects to review/rewrite such
contributions before they'd be merged. A NeoForge port done substantially
by an AI agent falls squarely under this. Don't open a normal PR to
`EVV1E/waylandcraft` claiming this as a clean human contribution - disclose
it and mark it draft, or expect it to be treated as reference material
rather than mergeable code. Realistically, given this is a second loader
entirely, the maintainer may want a multi-loader project restructure
(e.g. Architectury-style common/fabric/neoforge split) before merging
anything here at all, rather than merging a single-purpose NeoForge fork -
worth asking upstream before investing in a full port if the goal is
actually landing in their `main`, rather than just having a working
1.21.1 build for our own modpack.

## Repos involved

- `~/Git/waylandcraft` (this repo) - `origin` = `git@github.com:EVV1E/waylandcraft.git`
  (upstream, Fabric/26.1.2). Port work happens on the `neoforge-1.21.1`
  branch here.
- `git@github.com:meltingscales/waylandcraft-neoforge-1.21.1.git` - personal
  fork remote (`fork`) for pushing the port branch without touching upstream.
- `~/Git/MinecraftModpackDrakonixTechPack` - the modpack this is ultimately
  for. `pack/pack.toml` has the exact NeoForge/Minecraft versions pinned
  (21.1.251 / 1.21.1). Once a build exists, add it via
  `packwiz curseforge add` / `packwiz modrinth add` (once published) or
  `packwiz url add` (pointing at a GitHub release asset) from inside
  `pack/`, same as every other mod in that pack.
