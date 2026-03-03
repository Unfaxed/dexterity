package me.c7dev.dexterity.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.displays.animation.Animation;

public class AnimationEndEvent extends Event {

	private Player p;
	private Animation a;
	
	/**
	 * Event called when a display's animation terminates
	 * @param p_ The player involved, if there is one (such as for RideAnimation)
	 * @param a_
	 */
	public AnimationEndEvent(Player p_, Animation a_) {
		p = p_;
		a = a_;
	}
	
	public Player getPlayer() {
		return p;
	}
	public Animation getAnimation() {
		return a;
	}
	public DexterityDisplay getDisplay() {
		return a.getDisplay();
	}
	
	private static final HandlerList handlers = new HandlerList();
	
	public HandlerList getHandlers() {
		return handlers;
	}
	
	static public HandlerList getHandlerList() {
		return handlers;
	}
	
}
