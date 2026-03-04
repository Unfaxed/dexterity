package me.c7dev.dexterity.interaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.c7dev.dexterity.Dexterity;
import me.c7dev.dexterity.util.OrientationKey;
import me.c7dev.dexterity.util.RollOffset;

public class ClickDataCache {
	
	private static final long MS_UNTIL_DELETE = 30*1000;
	
	private final HashMap<OrientationKey, RollOffset> rollOffsets = new HashMap<>();
	private final HashMap<OrientationKey, Vector[]> axes = new HashMap<>();
	
	public ClickDataCache(Dexterity plugin) {
		new BukkitRunnable() {
			@Override
			public void run() {
				List<OrientationKey> toRemove = new ArrayList<>();
				long time = System.currentTimeMillis();

				for (OrientationKey key : rollOffsets.keySet()) if (time - key.getLastUsedTime() >= MS_UNTIL_DELETE) toRemove.add(key);
				for (OrientationKey key : toRemove) rollOffsets.remove(key);
				toRemove.clear();

				for (OrientationKey key : axes.keySet()) if (time - key.getLastUsedTime() >= MS_UNTIL_DELETE) toRemove.add(key);
				for (OrientationKey key : toRemove) axes.remove(key);
			}
		}.runTaskTimer(plugin, 0, MS_UNTIL_DELETE*20l/1000);
	}
	
	
	RollOffset getCachedRollOffset(OrientationKey key) {
		return rollOffsets.get(key);
	}
	
	Vector[] getCachedAxis(OrientationKey key) {
		return axes.get(key);
	}
	
	void setCachedRollOffset(OrientationKey key, RollOffset ro) {
		rollOffsets.put(key, ro);
	}
	
	void cacheAxis(OrientationKey key, Vector[] axisSet) {
		axes.put(key, axisSet);
	}

}
