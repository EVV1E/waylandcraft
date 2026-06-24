package dev.evvie.waylandcraft.polymer;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

/**
 * Every direct reference to a Polymer class in this mod lives here.
 * Only ever called from a site already guarded by
 * FabricLoader.isModLoaded("polymer-core") — see WaylandCraftCommon.onInitialize().
 *
 * Polymer is used purely for resource-pack serving: it bundles this mod's
 * assets (textures, models, item definitions) into a single combined pack
 * pushed to every connecting player, so the window item renders correctly
 * even for players who have Polymer but not WaylandCraft.
 */
public class PolymerIntegration {

    /**
     * Feeds this mod's assets into Polymer's combined resource pack,
     * which gets pushed to every connecting player.
     */
    public static void registerResourcePackAssets() {
        PolymerResourcePackUtils.addModAssets(WaylandCraftCommon.MOD_ID);
    }

}
