package dev.evvie.waylandcraft.desktop;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;

public class XDGDesktopManager {
	
	private final WaylandCraft wlc;
	private ArrayList<DesktopEntry> systemEntries = null;
	private Thread systemEntryFetchThread;
	
	public XDGDesktopManager(WaylandCraft wlc) {
		this.wlc = wlc;
		systemEntryFetchThread = new Thread(this::loadSystemEntries);
		systemEntryFetchThread.start();
	}
	
	private void loadSystemEntries() {
		/* Calling this in a separate thread is probably a huge crime but I'm desperate */
		
		Instant start = Instant.now();
		
		RawDesktopEntry[] rawEntries = wlc.bridge.loadSystemDesktopEntries();
		ArrayList<DesktopEntry> systemEntries = new ArrayList<DesktopEntry>();
		for(RawDesktopEntry raw : rawEntries) {
			systemEntries.add(new DesktopEntry(raw.appId, raw.name, raw.genericName, raw.exec, raw.execTerminal, raw.comment, raw.keywords, raw.categories, raw.visible, raw.iconPath));
		}
		this.systemEntries = systemEntries;
		
		WaylandCraftCommon.LOGGER.info("Completed desktop entry loading in " + Duration.between(start, Instant.now()).toMillis() / 1000.0f + "s");
		
		Thread iconPreloadThread = new Thread(this::preloadIcons);
		iconPreloadThread.start();
	}
	
	private void preloadIcons() {
		Instant start = Instant.now();
		
		for(DesktopEntry entry : systemEntries) {
			entry.preloadIcon();
		}
		
		WaylandCraftCommon.LOGGER.info("Completed icon preloading in " + Duration.between(start, Instant.now()).toMillis() / 1000.0f + "s");
	}
	
	private boolean completeFetch() {
		boolean done = false;
		try {
			done = systemEntryFetchThread.join(Duration.ZERO);
		} catch(InterruptedException e) {
		}
		
		return done;
	}
	
	public List<DesktopEntry> entries() {
		if(!completeFetch()) {
			return new ArrayList<DesktopEntry>();
		}
		
		ArrayList<DesktopEntry> entries = new ArrayList<DesktopEntry>();
		entries.addAll(systemEntries);
		return entries;
	}
	
	public @Nullable DesktopEntry forAppId(String appId) {
		if(appId == null) return null;
		if(!completeFetch()) {
			return null;
		}
		
		for(DesktopEntry entry : systemEntries) {
			if(entry.appId.equals(appId)) return entry;
		}
		return null;
	}
	
}
