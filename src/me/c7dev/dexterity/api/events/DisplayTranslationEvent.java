package me.c7dev.dexterity.api.events;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.c7dev.dexterity.displays.DexterityDisplay;

public class DisplayTranslationEvent extends Event {
	
	private DexterityDisplay d;
	private Location from, to;
	
	/**
	 * Event called when a selection is rotated, such as with a command or the API
	 * 
	 * @param display
	 * @param from
	 * @param to
	 */
	public DisplayTranslationEvent(DexterityDisplay display, Location from, Location to) {
		this.from = from.clone();
		this.to = to.clone();
		d = display;
	}
	
	public DexterityDisplay getDisplay() {
		return d;
	}
	
	public Location getFrom() {
		return from.clone();
	}
	
	public Location getTo() {
		return to.clone();
	}
	
	private static final HandlerList handlers = new HandlerList();
	
	public HandlerList getHandlers() {
		return handlers;
	}
	
	static public HandlerList getHandlerList() {
		return handlers;
	}

}
