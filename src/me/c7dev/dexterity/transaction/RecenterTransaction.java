package me.c7dev.dexterity.transaction;

import org.bukkit.Color;
import org.bukkit.Location;

import me.c7dev.dexterity.displays.DexterityDisplay;

public class RecenterTransaction implements Transaction {
	
	private Location oldLoc, newLoc = null;
	private DexterityDisplay disp;
	private boolean isUndone = false, isCommitted = false;
	
	public RecenterTransaction(DexterityDisplay d) {
		disp = d;
		oldLoc = d.getCenter();
	}
	
	public void commit(Location loc) {
		if (isCommitted || loc == null) return;
		newLoc = loc.clone();
	}
	
	public DexterityDisplay undo() {
		disp.setCenter(oldLoc);
		disp.getPlugin().api().markerPoint(oldLoc, Color.AQUA, 4);
		return null;
	}
	
	public void redo() {
		disp.setCenter(newLoc);
		disp.getPlugin().api().markerPoint(newLoc, Color.AQUA, 4);
	}
	
	public boolean isPossible() {
		return true;
	}

	public boolean isUndone() {
		return isUndone;
	}
	
	public boolean isCommitted() {
		return isCommitted;
	}
}
