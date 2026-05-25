package dev.evvie.waylandcraft.render.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;

/**
 * IrisCompat
 *
 * This is an example Class to detect if Iris is loaded and a shader pack is in use,
 * this could be used in RenderUtils to separate the custom rendering pipeline from 
 * the Entity rendering pipeline so shaders can be compatible until a better alternative 
 * is made.
 */
public class IrisCompat {
  private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");

  public static boolean isActive() {
    return IRIS_LOADED && IrisApi.getInstance().isShaderPackInUse();
  }
}
