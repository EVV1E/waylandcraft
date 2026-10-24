package dev.evvie.waylandcraft.bridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.lwjgl.system.Platform;

import dev.evvie.waylandcraft.WaylandCraftCommon;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class WaylandCraftNativeLibLoader implements PreLaunchEntrypoint {
	
	public static boolean fallbackMode = false;
	
	@Override
	public void onPreLaunch() {
		if(Platform.get() != Platform.LINUX) {
			fallbackMode = true;
			return;
		}
		
		WaylandCraftNativeLibLoader.loadLib();
	}
	
	private static void loadLib() {
		boolean loaded = false;
		InputStream inputStream = openNativeLibraryFromJar();
		if(inputStream != null) {
			try {
				byte[] data = inputStream.readAllBytes();
				inputStream.close();
				
				File temp = File.createTempFile("waylandcraft-", "-libwaylandcraft.so");
				temp.deleteOnExit();
				
				FileOutputStream outputStream = new FileOutputStream(temp);
				outputStream.write(data);
				outputStream.close();
				
				System.load(temp.getAbsolutePath());
				loaded = true;
				
				WaylandCraftCommon.LOGGER.info("Loaded native library from jar");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(!loaded) {
			WaylandCraftCommon.LOGGER.info("Native library could not be loaded from jar. Attempting to load from system");
			System.loadLibrary("waylandcraft");
		}
	}
	
	private static InputStream loadResource(String path) {
		WaylandCraftCommon.LOGGER.info("Looking for '" + path + "'...");
		return WaylandCraftBridge.class.getResourceAsStream(path);
	}
	
	private static InputStream openNativeLibraryFromJar() {
		InputStream stream = null;
		
		/* Attempt to load manually built native library */
		stream = loadResource("/libwaylandcraft.so");
		if(stream != null) return stream;
		
		/* Attempt to load from release library path */
		String arch;
		switch(Platform.getArchitecture()) {
		case X64: arch = "x86_64"; break;
		case ARM64: arch = "arm64"; break;
		default: arch = null; break;
		}
		
		if(arch != null) {
			String platform = "linux-gnu-" + arch;
			stream = loadResource("/libwaylandcraft-" + platform + ".so");
			if(stream != null) return stream;
		}
		
		return null;
	}
	
}
