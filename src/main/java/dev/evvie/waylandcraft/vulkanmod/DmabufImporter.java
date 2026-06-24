package dev.evvie.waylandcraft.vulkanmod;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.vulkanmod.gl.VkGlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports a Wayland DMA-BUF surface (delivered via WaylandCraft as an EGLImage)
 * into VulkanMod's texture system via EGL/GLES3 CPU readback.
 *
 * Alpha masking strategy:
 *   When xdg_surface geometry is available (set by WLCSurfaceMixin before each
 *   import), pixels inside the geometry bounds are forced to alpha=255 (opaque)
 *   to handle Ghostty background-opacity. Pixels outside the bounds (CSD shadow
 *   and border area) are forced to alpha=0 (transparent) so they don't appear
 *   as a black border in Minecraft.
 *
 *   When no geometry is available (first frame, or pre-display), the fallback
 *   forces all non-zero alpha pixels to 255.
 */
public final class DmabufImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("waylandcraft/vulkanmod/DmabufImporter");

    // Geometry captured by WLCSurfaceMixin from xdg_surface.set_window_geometry.
    // All on render thread — no synchronisation needed.
    private static int pendingGeoX = -1;
    private static int pendingGeoY = -1;
    private static int pendingGeoW = -1;
    private static int pendingGeoH = -1;

    public static void setPendingGeometry(int x, int y, int w, int h) {
        pendingGeoX = x;
        pendingGeoY = y;
        pendingGeoW = w;
        pendingGeoH = h;
    }

    public static void clearPendingGeometry() {
        pendingGeoW = -1;
    }

    private DmabufImporter() {}

    public static boolean importIntoVulkanMod(int glTextureId,
                                               long eglDisplay,
                                               long eglImage,
                                               int  width,
                                               int  height) {
        if (eglDisplay == 0L || eglImage == 0L) return false;
        if (width <= 0 || height <= 0)          return false;

        long pixels = EglImageReader.readPixels(eglDisplay, eglImage, width, height);
        if (pixels == 0L) return false;

        // Consume the pending geometry (valid for this import only).
        int geoX = pendingGeoX;
        int geoY = pendingGeoY;
        int geoW = pendingGeoW;
        int geoH = pendingGeoH;
        pendingGeoW = -1;

        try {
            if (geoW > 0 && geoH > 0) {
                // ── Geometry-based alpha masking ───────────────────────────────
                // Pixels inside the xdg geometry (window content + decorations):
                //   → alpha = 255 (fully opaque, handles background-opacity)
                // Pixels outside (CSD shadow/border):
                //   → alpha = 0 (fully transparent, no black border in Minecraft)
                int geoEndX = geoX + geoW;
                int geoEndY = geoY + geoH;

                for (int y = 0; y < height; y++) {
                    long row = pixels + (long) y * width * 4;
                    if (y < geoY || y >= geoEndY) {
                        // Entire row is shadow — zero alpha for all pixels
                        for (int x = 0; x < width; x++) {
                            MemoryUtil.memPutByte(row + x * 4L + 3, (byte) 0x00);
                        }
                    } else {
                        // Left shadow
                        for (int x = 0; x < geoX; x++) {
                            MemoryUtil.memPutByte(row + x * 4L + 3, (byte) 0x00);
                        }
                        // Content area:
                        //   alpha == 0 or 255 → leave as-is (no unnecessary write)
                        //   alpha <= 64        → force to 0   (CSD shadow bleeding into
                        //                        geometry-rect corners; Ghostty bg-opacity
                        //                        always produces alpha well above 64)
                        //   alpha > 64         → force to 255 (actual content, including
                        //                        semi-transparent bg-opacity pixels)
                        for (int x = geoX; x < geoEndX; x++) {
                            int a = MemoryUtil.memGetByte(row + x * 4L + 3) & 0xFF;
                            if (a != 0 && a != 255) {
                                MemoryUtil.memPutByte(row + x * 4L + 3, a <= 64 ? (byte) 0x00 : (byte) 0xFF);
                            }
                        }
                        // Right shadow
                        for (int x = geoEndX; x < width; x++) {
                            MemoryUtil.memPutByte(row + x * 4L + 3, (byte) 0x00);
                        }
                    }
                }
            } else {
                // ── Fallback: no geometry available ───────────────────────────
                // Apply the same threshold logic without geometry context:
                //   alpha == 0 or 255 → leave as-is
                //   alpha <= 64       → force to 0   (likely shadow)
                //   alpha > 64        → force to 255 (likely content)
                long p   = pixels;
                long end = pixels + (long) width * height * 4;
                while (p < end) {
                    int a = MemoryUtil.memGetByte(p + 3) & 0xFF;
                    if (a != 0 && a != 255) {
                        MemoryUtil.memPutByte(p + 3, a <= 64 ? (byte) 0x00 : (byte) 0xFF);
                    }
                    p += 4;
                }
            }

            // ── Upload to VulkanMod ────────────────────────────────────────────
            GlStateManager._pixelStore(GL33.GL_UNPACK_ROW_LENGTH, width);
            GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL33.GL_UNPACK_SKIP_ROWS, 0);
            VkGlTexture.texImage2D(
                GL11.GL_TEXTURE_2D, 0,
                GL11.GL_RGBA, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                pixels
            );
        } finally {
            MemoryUtil.nmemFree(pixels);
        }

        LOGGER.debug("[waylandcraft/vulkanmod/DmabufImporter] EGL readback {}×{} geo=({},{},{},{}) → GL tex {}",
                     width, height, geoX, geoY, geoW, geoH, glTextureId);
        return true;
    }
}
