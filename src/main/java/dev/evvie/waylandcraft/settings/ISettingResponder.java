package dev.evvie.waylandcraft.settings;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ISettingResponder {
	
	/**
	 * Called when the waylandcraft setting has changed
	 * 
	 * This allows synchronization of settings state across the compositor. Responders are immediately
	 * called on the observed setting when first registered. The responder may be fired multiple times
	 * on the same value, even if it didn't change.
	 * 
	 * @param value the current value. Can safely be cast to the expected type
	 */
	void onChangeSetting(@NotNull Object value);
	
}
