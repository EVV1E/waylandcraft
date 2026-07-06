package dev.evvie.waylandcraft.mixin;

import org.lwjgl.opengl.GL33;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.GpuTexture;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {
	
	@Shadow private int readFbo;
	@Shadow private int drawFbo;
	@Shadow private GlDevice device;
	
	/* Fix for copyTextureToTexture being broken because of the wrong coordinates.
	 * See MC-309619
	 */
	@Inject(method = "copyTextureToTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/DirectStateAccess;blitFrameBuffers(IIIIIIIIIIII)V"), cancellable = true)
	public void fixupCopyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destX, int destY, int sourceX, int sourceY, int width, int height, CallbackInfo info, @Local boolean isDepth) {
		((IDirectStateAccessMixin) device.directStateAccess()).invokeBlitFrameBuffers(
				this.readFbo,
				this.drawFbo,
				sourceX,
				sourceY,
				sourceX + width, /* CHANGED */
				sourceY + height, /* CHANGED */
				destX,
				destY,
				destX + width, /* CHANGED */
				destY + height, /* CHANGED */
				isDepth ? GL33.GL_DEPTH_BUFFER_BIT : GL33.GL_COLOR_BUFFER_BIT, GL33.GL_NEAREST
		);
		int error = GlStateManager._getError();
		if (error != 0) {
			throw new IllegalStateException("Couldn't perform copyToTexture for texture " + source.getLabel() + " to " + destination.getLabel() + ": GL error " + error);
		}
		info.cancel();
	}
	
}
