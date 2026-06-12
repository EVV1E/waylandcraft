package dev.evvie.waylandcraft.utils;

import java.util.ArrayList;

public interface IMyServerPlayer {
	
	void setItemGiveCooldown(int cooldown);
	int getItemGiveCooldown();
	
	ArrayList<Long> getAliveWindows();
	
}
