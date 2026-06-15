package me.c7dev.dexterity.transaction;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;

import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.util.DexBlock;

public class GlowTransaction implements Transaction {
	
	private HashMap<UUID, Color> previousGlow = new HashMap<>(), currentGlow = new HashMap<>();
	private Color allSameGlow;
	private final DexterityDisplay disp;
	private boolean isUndone = false, committed = false;
	
	public GlowTransaction(DexterityDisplay d) {
		disp = d;
		for (DexBlock block : d.getBlocks()) {
			previousGlow.put(block.getUniqueId(), getTransactionGlow(block));
		}
	}
	
	private Color getTransactionGlow(DexBlock b) {
		return b.getEntity().isGlowing() ? b.getEntity().getGlowColorOverride() : null;
	}

	@Override
	public DexterityDisplay undo() {
		if (!isCommitted()) throw new RuntimeException("Transaction must be committed");
		if (isUndone) return null;
		
		for (DexBlock block : disp.getBlocks()) { //load previous glow
			Color glow = previousGlow.get(block.getUniqueId());
			if (glow == null) block.getEntity().setGlowing(false);
			else {
				block.getEntity().setGlowColorOverride(glow);
				block.getEntity().setGlowing(true);
			}
		}
		
		isUndone = true;
		return null;
	}

	@Override
	public void redo() {
		if (!isCommitted()) throw new RuntimeException("Transaction must be committed");
		if (!isUndone) return;
		isUndone = false;
		
		if (currentGlow.size() > 0) {
			for (DexBlock block : disp.getBlocks()) {
				Color glow = currentGlow.get(block.getUniqueId());
				block.setGlow(glow);
			}
		} else {
			for (DexBlock block : disp.getBlocks()) block.setGlow(allSameGlow);
		}
	}
	
	public void commit() {
		if (isCommitted()) throw new RuntimeException("Cannot commit transaction twice");
		DexBlock[] blocks = disp.getBlocks();
		if (blocks.length == 0) throw new RuntimeException("Cannot commit an empty display");
		boolean allSame = true;
		allSameGlow = getTransactionGlow(blocks[0]);
		
		for (DexBlock block : blocks) { //currently the command only supports setting all to the same glow, but this is not an assumption held the API.
			Color glow = getTransactionGlow(block);
			if (allSameGlow != null && !allSameGlow.equals(glow)) {
				allSame = false; //not all the same
				allSameGlow = null;
			}
			currentGlow.put(block.getUniqueId(), glow);
		}
		
		if (allSame) currentGlow.clear(); //all the same glow, map memory can be released
		
		committed = true;
	}

	@Override
	public boolean isCommitted() {
		return committed;
	}

	@Override
	public boolean isUndone() {
		return isUndone;
	}

	@Override
	public boolean isPossible() {
		return true;
	}

}
