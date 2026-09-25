package dev.evvie.waylandcraft.compat;

import net.irisshaders.iris.api.v0.IrisApi;
import net.neoforged.fml.ModList;

public class IrisCompat {
	
	public static boolean isShaderActive() {
		if(!ModList.get().isLoaded("iris")) return false;
		return IrisApi.getInstance().isShaderPackInUse();
	}
	
}
