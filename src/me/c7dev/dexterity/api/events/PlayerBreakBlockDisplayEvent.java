package me.c7dev.dexterity.api.events;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.c7dev.dexterity.util.ClickedBlockDisplay;

public class PlayerBreakBlockDisplayEvent extends Event implements Cancellable {
	
	private Player player;
	private ClickedBlockDisplay clicked;
	private boolean cancelled = false;
	
	/**
	 * Event called when a player has clicked a block display entity
	 */
	public PlayerBreakBlockDisplayEvent(Player player, ClickedBlockDisplay clicked) {
		this.player = player;
		this.clicked = clicked;
	}
	
	/**
	 * Gets the player who clicked the block display
	 * @return
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * Gets the living block display entity that is about to be broken
	 * @return
	 */
	public BlockDisplay getBlockDisplay() {
		return clicked.getBlockDisplay();
	}
	
	/**
	 * Gets the cardinal direction of the face that was clicked
	 * @return
	 */
	public BlockFace getClickedBlockFace() {
		return clicked.getBlockFace();
	}
	
	/**
	 * Gets the location that exists on the surface of the clicked block that is the precise point that the player is looking at
	 * @return
	 */
	public Location getPreciseClickLocation() {
		return clicked.getClickLocation();
	}
	
	/**
	 * Gets the location of the center of the clicked block display
	 * @return
	 */
	public Location getClickedLocation() {
		return clicked.getDisplayCenterLocation();
	}
	
	public boolean isCancelled() {
		return cancelled;
	}
	
	/**
	 * Returns the number of blocks of distance from the surface of the block to the player's eye
	 * @return
	 */
	public double getPreciseDistanceFromEye() {
		return clicked.getDistance();
	}
	
	public void setCancelled(boolean b) {
		cancelled = b;
	}
	
	private static final HandlerList handlers = new HandlerList();
	
	public HandlerList getHandlers() {
		return handlers;
	}
	
	static public HandlerList getHandlerList() {
		return handlers;
	}

}
