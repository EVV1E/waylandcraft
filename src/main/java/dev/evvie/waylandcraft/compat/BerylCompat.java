package dev.evvie.waylandcraft.compat;

import net.fabricmc.loader.api.FabricLoader;

public class BerylCompat {

	/**
	 * Returns true when Beryl is loaded. When active, WaylandCraft falls back
	 * to entity-based rendering so that Beryl's pipeline does not corrupt the
	 * window framebuffers — the same path used for Iris shader packs.
	 *
	 * Beryl does not currently expose a public Java API to query whether its
	 * effects are actually enabled at a given moment (unlike Iris, which has
	 * IrisApi.isShaderPackInUse()). Presence of the mod is used as a proxy:
	 * if Beryl is installed it is assumed to be active. If Beryl ships a
	 * stable API in a future release, this method can be tightened to call it
	 * via an inner holder class (see IrisCompat for the pattern).
	 */
	public static boolean isShaderActive() {
		return FabricLoader.getInstance().isModLoaded("beryl");
	}

}
