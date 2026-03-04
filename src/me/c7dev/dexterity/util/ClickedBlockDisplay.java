package me.c7dev.dexterity.util;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Vector;

public class ClickedBlockDisplay {
	
	private BlockDisplay block;
	private BlockFace blockFace;
	private Vector offset, normal, upDir, eastDir, southDir;
	private Location loc, center;
	private double distance;
	private RollOffset ro = null;
	
	private Vector getNormal(BlockFace f, Vector up, Vector east, Vector south) {
		switch(f) {
		case UP: return up.clone().normalize();
		case DOWN: return up.clone().multiply(-1).normalize();
		case SOUTH: return south.clone().normalize();
		case NORTH: return south.clone().multiply(-1).normalize();
		case EAST: return east.clone().normalize();
		case WEST: return east.clone().multiply(-1).normalize();
		default: return new Vector(0, 0, 0);
		}
	}
	
	public ClickedBlockDisplay(BlockDisplay block, BlockFace blockFace, Vector offset, Location loc, Location centerLoc, 
			Vector up_dir, Vector east_dir, Vector south_dir, double dist) {
		this.block = block;
		this.blockFace = blockFace;
		this.offset = offset;
		this.loc = loc;
		this.center = centerLoc;
		this.upDir = up_dir.normalize();
		this.eastDir = east_dir.normalize();
		this.southDir = south_dir.normalize();
		this.normal = getNormal(blockFace, up_dir, east_dir, south_dir);
		this.distance = dist;
	}
	
	public void setRollOffset(RollOffset ro) {
		if (this.ro != null) throw new RuntimeException("Cannot set roll offset twice");
		this.ro = ro;
	}
	
	/**
	 * The raw display entity that was clicked
	 * @return
	 */
	public BlockDisplay getBlockDisplay() {
		return block;
	}
	
	/**
	 * Which face of the block display was clicked, accounting for rotation of the block display
	 * @return
	 */
	public BlockFace getBlockFace() {
		return blockFace;
	}
	
	/**
	 * Retrieves the vector along the plane of the block face from the center of the face to the precise location of the click
	 * @return Unmodifiable vector
	 */
	public Vector getOffsetFromFaceCenter() {
		return offset.clone();
	}
	
	/**
	 * Retrieves the precise location on the block face that the player clicked
	 * @return Unmodifiable location
	 */
	public Location getClickLocation() {
		return loc.clone();
	}
	
	/**
	 * Retrieves the center of the block display entity
	 * @return Unmodifiable location
	 */
	public Location getDisplayCenterLocation() {
		return center.clone();
	}
	
	/**
	 * Retrieves a unit vector that is perpendicular to the clicked block face
	 * @return Unmodifiable unit vector
	 */
	public Vector getNormal() {
		return normal.clone();
	}
	
	/**
	 * Retrieves the entity's relative up direction basis vector
	 * @return Unmodifiable unit vector
	 */
	public Vector getUpDir() {
		return upDir.clone();
	}
	
	/**
	 * Retrieves the entity's relative east direction basis vector
	 * @return Unmodifiable unit vector
	 */
	public Vector getEastDir() {
		return eastDir.clone();
	}
	
	/**
	 * Retrieves the entity's relative south direction basis vector
	 * @return Unmodifiable unit vector
	 */
	public Vector getSouthDir() {
		return southDir.clone();
	}
	
	/**
	 * Retrieves the distance from the player's eye location to the block face in units of blocks
	 * @return
	 */
	public double getDistance() {
		return distance;
	}
	
	/**
	 * Retrieves a wrapper for the calculated roll offset, if roll is used in the block display's rotation
	 * @return
	 */
	public RollOffset getRollOffset() {
		return ro;
	}

}
