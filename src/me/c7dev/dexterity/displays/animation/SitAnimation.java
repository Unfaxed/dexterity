package me.c7dev.dexterity.displays.animation;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.util.Vector;

import me.c7dev.dexterity.api.events.DisplayTranslationEvent;
import me.c7dev.dexterity.displays.DexterityDisplay;

public class SitAnimation extends Animation implements RideableAnimation, Listener {
	
	private final double seatOffsetY = -1.5;
	private ArmorStand mount;
	private Player p;
	private Vector seatOffset = new Vector(0, seatOffsetY, 0);
	private boolean freezeDismountEvent = false;

	public SitAnimation(DexterityDisplay display) {
		super(display, 1);
		Bukkit.getPluginManager().registerEvents(this, display.getPlugin());
	}
	
	private void spawnMount() {
		removeMount();
		mount = getDisplay().getPlugin().spawn(getDisplay().getCenter().add(seatOffset), ArmorStand.class, a -> {
			a.setSilent(true);
			a.setGravity(false);
			a.setVisible(false);
		});
	}
	
	public void refreshMountedPlayer() {
		if (mount == null) {
			p = null;
			return;
		}
		if (mount.getPassengers().size() == 0) p = null;
	}
	
	public Player getMountedPlayer() {
		refreshMountedPlayer();
		return p;
	}
	
	public boolean mount(Player player) {
		if (p != null) return false;
		spawnMount();
		p = player;
		mount.addPassenger(player);
		return true;
	}
	
	private void removeMount() {
		if (mount != null) mount.remove();
		mount = null;
	}
	
	public void dismount() {
		if (p == null) return;
		if (mount != null) {
			mount.removePassenger(p);
			removeMount();
		}
	}
	
	public void setSeatOffset(Vector v) {
		v = v.clone();
		v.setY(v.getY() + seatOffsetY);
		Vector diff = v.clone().subtract(seatOffset);
		if (mount != null) mount.teleport(mount.getLocation().add(diff));
		seatOffset = v;
	}
	
	public Vector getSeatOffset() {
		return seatOffset.clone().subtract(new Vector(0, seatOffsetY, 0));
	}
	
	@EventHandler
	public void onDismountEvent(EntityDismountEvent e) {
		if (p == null || mount == null || freezeDismountEvent || !e.getEntity().getUniqueId().equals(p.getUniqueId())) return;
		dismount();
	}
	
	@EventHandler
	public void onDisplayMove(DisplayTranslationEvent e) {
		if (!e.getDisplay().equals(super.getDisplay())) return;
		refreshMountedPlayer();
		if (mount == null) return;
		freezeDismountEvent = true;
		if (p != null) mount.removePassenger(p);
		mount.teleport(e.getTo().add(seatOffset));
		if (p != null) mount.addPassenger(p);
		freezeDismountEvent = false;
	}
	
	@Override
	public void stop() {
		super.kill();
		p = null;
		removeMount();
		HandlerList.unregisterAll(this);
	}

}
