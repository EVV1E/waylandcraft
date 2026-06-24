package dev.evvie.waylandcraft.compat;

import net.fabricmc.loader.api.FabricLoader;

public class BerylCompat {

	/**
	 * Returns true when Beryl's shader pipeline is actively in use.
	 *
	 * RenderingPipeline.isUsingShaderPipeline() is the public API equivalent of
	 * IrisApi.isShaderPackInUse(): it returns false when Beryl is loaded but the
	 * shader pipeline has been toggled off by the user.  The actual call is
	 * deferred into an inner holder class so that the Beryl class reference is
	 * never linked on installs that don't have Beryl.
	 */
	public static boolean isShaderActive() {
		if (!FabricLoader.getInstance().isModLoaded("beryl")) return false;
		return BerylHolder.isUsingShaderPipeline();
	}

	private static final class BerylHolder {
		static boolean isUsingShaderPipeline() {
			return net.beryl.render.RenderingPipeline.isUsingShaderPipeline();
		}
	}

}
