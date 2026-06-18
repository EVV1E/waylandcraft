package dev.evvie.waylandcraft.mixin;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.opengl.GlBackend;

@Mixin(GlBackend.class)
public class GlBackendMixin {
	
	@Inject(method = "setWindowHints", at = @At("TAIL"))
	public void changeContextApi(CallbackInfo info) {
		if(Platform.get() != Platform.LINUX) return;
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_EGL_CONTEXT_API);
	}
	
}
