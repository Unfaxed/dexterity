package me.c7dev.dexterity.transaction;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.Mask;

/**
 * Essentially stores the BlockTransactionLine list
 * This is the most common transaction type
 */
public class BlockTransaction implements Transaction {
	
	protected HashMap<UUID, BlockTransactionLine> trans = new HashMap<>();
	protected boolean isUndone = false, isCommitted = false;
	private Location oldCenter, newCenter;
	private DexterityDisplay disp;
	
	public BlockTransaction() {
	}
	
	public BlockTransaction(DexterityDisplay disp) {
		this(disp, null);
	}
	
	public BlockTransaction(DexterityDisplay disp, Mask mask) {
		if (disp == null) throw new IllegalArgumentException("Display cannot be null on transaction!");
		this.disp = disp;
		oldCenter = disp.getCenter();
		if (mask == null) {
			for (DexBlock db : disp.getBlocks()) trans.put(db.getEntity().getUniqueId(), new BlockTransactionLine(db));
		} else {
			for (DexBlock db : disp.getBlocks()) {
				if (mask.isAllowed(db.getEntity().getBlock().getMaterial())) trans.put(db.getEntity().getUniqueId(), new BlockTransactionLine(db));
			}
		}
	}
	
	public void addBlock(DexBlock block) {
		if (trans.containsKey(block.getEntity().getUniqueId())) return;
		trans.put(block.getEntity().getUniqueId(), new BlockTransactionLine(block));
	}
	
	public void commitBlock(DexBlock db) {
		BlockTransactionLine t = trans.get(db.getEntity().getUniqueId());
		if (t == null) return;
		
		t.commit(db.getState());
		isCommitted = true;
	}
	
	public void commit(DexBlock[] blocks) {
		commit(blocks, null, false);
	}
	
	public void commit(DexBlock[] blocks, Mask mask, boolean include_removed_blocks) {
		if (isCommitted) return;
		isCommitted = true;
		if (mask == null) {
			for (DexBlock db : blocks) commitBlock(db);
		} else {
			for (DexBlock db : blocks) {
				if (mask.isAllowed(db.getEntity().getBlock().getMaterial())) commitBlock(db);
			}
		}
		
		if (include_removed_blocks) includeRemovedBlocks();
	}
	
	public void includeRemovedBlocks() {
		for (Entry<UUID, BlockTransactionLine> entry : trans.entrySet()) {
			if (!entry.getValue().isCommitted() && Bukkit.getEntity(entry.getKey()) == null) entry.getValue().commit(null);
		}
	}
	
	public void commitEmpty() { //no blocks
		isCommitted = true;
	}
	
	public void commitCenter(Location new_loc) {
		if (new_loc == null) return;
		newCenter = new_loc.clone();
	}
	
	public DexterityDisplay undo() {
		if (!isCommitted || isUndone) return null;
		isUndone = true;
		for (Entry<UUID, BlockTransactionLine> entry : trans.entrySet()) {
			entry.getValue().refresh(disp.getPlugin());
			entry.getValue().undo();
		}
		if (newCenter != null && oldCenter != null) disp.setCenter(oldCenter);
		return null;
	}
	
	public void redo() {
		if (!isCommitted || !isUndone) return;
		isUndone = false;
		
		for (Entry<UUID, BlockTransactionLine> entry : trans.entrySet()) entry.getValue().redo();
		if (newCenter != null && oldCenter != null) disp.setCenter(newCenter);
	}
	
	public boolean isPossible() {
		return true;
	}
	
	public boolean isCommitted() {
		return isCommitted;
	}
	
	public boolean isUndone() {
		return isUndone;
	}

}
