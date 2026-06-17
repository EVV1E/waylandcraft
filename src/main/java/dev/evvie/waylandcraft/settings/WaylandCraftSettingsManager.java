package dev.evvie.waylandcraft.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.minecraft.client.Minecraft;

public class WaylandCraftSettingsManager {
	
	private final WaylandCraft wlc;
	private final Gson gson = new Gson();
	
	private File settingsDir;
	private File keymapFile;
	private File settingsFile;
	
	public WaylandCraftSettingsManager(WaylandCraft wlc) {
		this.wlc = wlc;
		
		try {
			init();
		} catch(IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to read settings storage!");
		}
	}
	
	private void init() throws IOException {
		/* Ensure settings directory */
		settingsDir = new File(Minecraft.getInstance().gameDirectory, "waylandcraft");
		if(!settingsDir.exists()) {
			settingsDir.mkdir();
		}
		else if(!settingsDir.isDirectory()) {
			throw new IOException("Waylandcraft settings directory exists but is not a directory");
		}
		
		/* Read keymap override */
		keymapFile = new File(settingsDir, "keymap.txt");
		
		String keymap = tryReadKeymapFromFile();
		if(keymap == null) {
			keymap = tryReadKeymapFromSystem();
		}
		
		if(keymap != null) {
			if(!wlc.bridge.setKeymapFromStr(keymap)) {
				WaylandCraftCommon.LOGGER.error("Failed to load keymap!");
			}
		}
		
		/* Ensure settings file */
		boolean createSettings = false;
		settingsFile = new File(settingsDir, "settings.json");
		if(!settingsFile.exists()) {
			settingsFile.createNewFile();
			createSettings = true;
		}
		else if(!settingsFile.isFile()) {
			throw new IOException("Waylandcraft settings.json exists but is not a file");
		}
		
		if(createSettings) {
			// Create default settings
			wlc.settings = new WaylandCraftSettings();
			writeSettings();
		}
		
		readSettings();
	}
	
	private String tryReadKeymapFromSystem() {
		// Try running xkbcli to get keymap
		String keymap = null;
		try {
			Process process = new ProcessBuilder("xkbcli", "dump-keymap").start();
			byte[] data = process.getInputStream().readAllBytes();
			keymap = new String(data);
			
			int exitCode = process.waitFor();
			if(exitCode != 0) {
				keymap = null;
				WaylandCraftCommon.LOGGER.error("xkbcli exited with error " + exitCode);
			}
		} catch (IOException | InterruptedException e) {
			WaylandCraftCommon.LOGGER.error("xkbcli invoke failed!", e);
		}
		if(keymap == null) {
			WaylandCraftCommon.LOGGER.error("Failed to dump keymap using xkbcli");
		}
		return keymap;
	}
	
	private String tryReadKeymapFromFile() {
		if(!(keymapFile.exists() && keymapFile.isFile())) return null;
		
		try {
			FileInputStream stream = new FileInputStream(keymapFile);
			byte[] data = stream.readAllBytes();
			String keymap = new String(data);
			stream.close();
			return keymap;
		} catch(IOException e) {
			WaylandCraftCommon.LOGGER.info("Error reading keymap file!", e);
			return null;
		}
	}
	
	public void readSettings() {
		try(FileReader reader = new FileReader(settingsFile)) {
			wlc.settings = gson.fromJson(reader, WaylandCraftSettings.class);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void writeSettings() {
		String json = gson.toJson(wlc.settings, WaylandCraftSettings.class);
		try(FileWriter writer = new FileWriter(settingsFile)) {
			writer.write(json);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// Set an int setting and write it to file
	public void setIntSetting(String name, int value) {
		wlc.settings.setIntSetting(name, value);
		writeSettings();
	}
	
	// Set a boolean setting and write it to file
	public void setBooleanSetting(String name, boolean value) {
		wlc.settings.setBooleanSetting(name, value);
		writeSettings();
	}
	
	// Set a text setting and write it to file
	public void setTextSetting(String name, String value) {
		wlc.settings.setTextSetting(name, value);
		writeSettings();
	}
	
	// Get an int setting
	public int getIntSetting(String name) {
		return wlc.settings.getIntSetting(name);
	}
	
	// Set a boolean setting
	public boolean getBooleanSetting(String name) {
		return wlc.settings.getBooleanSetting(name);
	}
	
	// Set a text setting
	public String getTextSetting(String name) {
		return wlc.settings.getTextSetting(name);
	}
	
}
