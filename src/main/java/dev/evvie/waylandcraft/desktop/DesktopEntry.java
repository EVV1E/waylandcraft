package dev.evvie.waylandcraft.desktop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

public class DesktopEntry {
	
	public @NotNull String appId;
	public @Nullable String name;
	public @Nullable String genericName;
	public @Nullable String exec;
	public boolean execTerminal;
	public final String comment;
	public final String[] keywords;
	public final String[] categories;
	public boolean visible;
	private DesktopIcon icon = null;
	
	public DesktopEntry(String appId, String name, String genericName, String exec, boolean execTerminal, String comment, String[] keywords, String[] categories, boolean visible, String iconPath) {
		this.appId = appId;
		this.name = name;
		this.genericName = genericName;
		this.exec = exec;
		this.execTerminal = execTerminal;
		this.comment = comment;
		this.keywords = keywords;
		this.categories = categories;
		this.visible = visible;
		
		if(iconPath != null) this.icon = new DesktopIcon(appId, iconPath);
	}
	
	public ResourceLocation getIcon() {
		if(icon == null) return null;
		return icon.getTextureLocation();
	}
	
	protected void preloadIcon() {
		if(icon == null) return;
		icon.preload();
	}
	
	@Override
	public String toString() {
		return "DesktopEntry [appId: " + appId + ", name: " + name + ", genericName: " + genericName + ", exec: '" + exec + "', execTerminal: " + execTerminal + ", visible: " + visible + ", icon: " + icon + "]";
	}
	
}
