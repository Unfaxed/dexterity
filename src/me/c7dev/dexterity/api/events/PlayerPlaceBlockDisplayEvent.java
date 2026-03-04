package me.c7dev.dexterity.api.events;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.c7dev.dexterity.util.ClickedBlockDisplay;

public class PlayerPlaceBlockDisplayEvent extends Event implements Cancellable {
	
	private Player player;
	private ClickedBlockDisplay clicked;
	private BlockData blockData;
	private boolean cancelled = false;
	
	/**
	 * Event called when a player has clicked a block display entity
	 */
	public PlayerPlaceBlockDisplayEvent(Player player, ClickedBlockDisplay clicked, BlockData blockData) {
		this.player = player;
		this.clicked = clicked;
		this.blockData = blockData;
	}
	
	/**
	 * Gets the player who clicked the block display
	 * @return
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * Returns the metadata of the block type that is about to be placed
	 * @return
	 */
	public BlockData getBlockDataToPlace() {
		return blockData;
	}
	
	/**
	 * Gets the living block display entity that the new display will be placed on
	 * @return
	 */
	public BlockDisplay getClickedBlockDisplay() {
		return clicked.getBlockDisplay();
	}
	
	/**
	 * Gets the cardinal direction of the block display face that was clicked
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
