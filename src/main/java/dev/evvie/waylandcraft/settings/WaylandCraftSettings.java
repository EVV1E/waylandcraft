package dev.evvie.waylandcraft.settings;

import java.lang.reflect.Field;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraftCommon;

/* Settings for waylandcraft
 * 
 * All of the fields here that do not have the "transient" modifier are written to the settings.json!
 * Because of that and because these fields are also accessed using reflection, just don't change their names,
 * unless you also update all the references and are okay with user settings breaking across updates.
 */
public class WaylandCraftSettings {
	
	/* The settings fields shouldn't have a modifier so that they're package-private.
	 * This avoids code directly setting the values.
	 */
	
	int pixelsPerBlock = 500;
	boolean windowAntialiasing = false;
	boolean focusOnHover = false;
	
	/* This is where the field names go to avoid typos */
	public static final String PIXELS_PER_BLOCK = "pixelsPerBlock";
	public static final String WINDOW_ANTIALIASING = "windowAntialiasing";
	public static final String FOCUS_ON_HOVER = "focusOnHover";
	
	/* This is where the getters go */
	
	public int getPixelsPerBlock() {
		return pixelsPerBlock;
	}
	
	public boolean getAntialiasing() {
		return windowAntialiasing;
	}
	
	public boolean getFocusOnHover() {
		return focusOnHover;
	}
	
	/* Methods to modifiy settings by name */
	
	protected void setIntSetting(String name, int value) {
		try {
			Field field = WaylandCraftSettings.class.getDeclaredField(name);
			field.setInt(this, value);
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			WaylandCraftCommon.LOGGER.error("Invalid setting accessed: '" + name + "' as int!");
			e.printStackTrace();
		}
	}
	
	protected void setBooleanSetting(String name, boolean value) {
		try {
			Field field = WaylandCraftSettings.class.getDeclaredField(name);
			field.setBoolean(this, value);
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			WaylandCraftCommon.LOGGER.error("Invalid setting accessed: '" + name + "' as boolean!");
			e.printStackTrace();
		}
	}
	
	// Get int setting. Returns null only when setting was not found.
	protected @Nullable Integer getIntSetting(String name) {
		try {
			Field field = WaylandCraftSettings.class.getDeclaredField(name);
			return field.getInt(this);
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			WaylandCraftCommon.LOGGER.error("Invalid setting accessed: '" + name + "' as int!");
			e.printStackTrace();
		}
		return null;
	}
	
	// Get boolean setting. Returns null only when setting was not found.
	protected @Nullable Boolean getBooleanSetting(String name) {
		try {
			Field field = WaylandCraftSettings.class.getDeclaredField(name);
			return field.getBoolean(this);
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			WaylandCraftCommon.LOGGER.error("Invalid setting accessed: '" + name + "' as boolean!");
			e.printStackTrace();
		}
		return null;
	}
	
}
