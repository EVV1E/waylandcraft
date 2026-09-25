package dev.evvie.waylandcraft.mixin;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;

// Replaces GlBackendMixin: 1.21.1 sets the GLFW window hints in the Window
// constructor. This only takes effect when NeoForge's early loading window is
// disabled, because otherwise the GLFW window already exists by the time this runs.
@Mixin(Window.class)
public class WindowMixin {
	
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
	public void changeContextApi(CallbackInfo info) {
		if(Platform.get() != Platform.LINUX) return;
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_EGL_CONTEXT_API);
	}
	
}
