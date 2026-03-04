package me.c7dev.dexterity;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import me.c7dev.dexterity.api.events.TransactionCompletionEvent;
import me.c7dev.dexterity.api.events.TransactionEvent;
import me.c7dev.dexterity.api.events.TransactionRedoEvent;
import me.c7dev.dexterity.api.events.TransactionUndoEvent;
import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.displays.schematics.Schematic;
import me.c7dev.dexterity.interaction.DexClickHandler;
import me.c7dev.dexterity.transaction.RemoveTransaction;
import me.c7dev.dexterity.util.DexUtils;

public class EventListeners implements Listener {
	
	private Dexterity plugin;
	private HashMap<UUID, Long> clickDelay = new HashMap<>();
	
	public EventListeners(Dexterity plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	public boolean clickDelay(UUID u) {
		int delay = 100;
		if (System.currentTimeMillis() - clickDelay.getOrDefault(u, 0l) < delay) return true;
		final long newdelay = System.currentTimeMillis() + delay;
		clickDelay.put(u, newdelay);
		new BukkitRunnable() {
			@Override
			public void run() {
				if (clickDelay.getOrDefault(u, 0l) == newdelay) clickDelay.remove(u);
			}
		}.runTaskLater(plugin, (int) (delay*0.02));
		return false;
	}
	
	@EventHandler
	public void onBlockClick(PlayerInteractEvent e) {
		if (clickDelay(e.getPlayer().getUniqueId())) return;
		new DexClickHandler(plugin, e);
	}
	
	@EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true) //placing display item
	public void onPlace(BlockPlaceEvent e) {
		if (e.isCancelled()) return;
		ItemStack hand = e.getItemInHand();
		if (hand == null || hand.getType() == Material.AIR) return;

		ItemMeta meta = hand.getItemMeta();
		NamespacedKey key = new NamespacedKey(plugin, "dex-schem-label");
		String schemName = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if (!plugin.api().checkSchematicExists(schemName)) return;
		
		ItemStack item = hand.clone();
		item.setAmount(1);
		e.setCancelled(true);
		Schematic schem = new Schematic(plugin, schemName);
		
		Vector diff = e.getBlock().getLocation().toVector().subtract(e.getBlockAgainst().getLocation().toVector());
		BoundingBox box = schem.getPlannedBoundingBox();
		Location loc = e.getBlock().getLocation();
		
		if (diff.getY() == 1) { //clicked up face
			Vector againstDims = DexUtils.getBlockDimensions(e.getBlockAgainst().getBlockData());
			if (e.getBlockAgainst().getBlockData() instanceof Slab) {
				Slab slab = (Slab) e.getBlockAgainst().getBlockData();
				if (slab.getType() != Slab.Type.BOTTOM) againstDims = new Vector(1, 1, 1);
			}
			else if (e.getBlockAgainst().getBlockData() instanceof TrapDoor) {
				TrapDoor td = (TrapDoor) e.getBlockAgainst().getBlockData();
				if (td.getHalf() == Bisected.Half.TOP) againstDims = new Vector(1, 1, 1);
			}
			loc.add(0.5, -box.getMinY() + againstDims.getY() - 1, 0.5);
		}
		else if (diff.getY() == -1) loc.add(0.5, 1 - box.getMaxY(), 0.5); //clicked down face
		else if (diff.getX() == 1) loc.add(-box.getMinX(), -box.getMinY(), 0.5); //clicked west face
		else if (diff.getX() == -1) loc.add(1 - box.getMaxX(), -box.getMinY(), 0.5); //clicked east face
		else if (diff.getZ() == 1) loc.add(0.5, -box.getMinY(), -box.getMinZ()); //clicked south face
		else if (diff.getZ() == -1) loc.add(0.5, -box.getMinY(), 1 - box.getMaxZ()); //clicked north face
		
		DexterityDisplay d = schem.paste(loc);
		d.setListed(false);
		d.addOwner(e.getPlayer());
		d.setDropItem(item, schemName);
		if (e.getPlayer().getGameMode() != GameMode.CREATIVE) e.getPlayer().getInventory().removeItem(item);
	}
	
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		DexSession session = plugin.getEditSession(e.getPlayer().getUniqueId());
		if (session == null || !session.isFollowing() || session.getSelected() == null) return;
		if (!session.getSelected().getCenter().getWorld().getName().equals(e.getPlayer().getWorld().getName())) {
			session.cancelEdit();
			session.setSelected(null, false);
			return;
		}
		
		Location loc = e.getPlayer().getLocation();
		if (!e.getPlayer().isSneaking()) loc = DexUtils.blockLoc(loc); //block location
		else loc.add(-0.5, 0, -0.5); //precise location
		
		loc.add(session.getFollowingOffset());
		
		Location center = session.getSelected().getCenter();
		if (loc.getX() == center.getX() && loc.getY() == center.getY() && loc.getZ() == center.getZ()) return;
		
		double cutoff = 0.005; //follow player
		if (Math.abs(e.getTo().getX() - e.getFrom().getX()) > cutoff || Math.abs(e.getTo().getY() - e.getFrom().getY()) > cutoff || Math.abs(e.getTo().getZ() - e.getFrom().getZ()) > cutoff) {
			session.getSelected().teleport(loc);
		}
	}
	
	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent e) {
		if (!e.getPlayer().hasPermission("worldedit.selection.pos") || !e.getPlayer().hasPermission("dexterity.command")) return;
		if (e.getMessage().equalsIgnoreCase("//pos1") || e.getMessage().equalsIgnoreCase("//pos2")) {
			DexSession s = plugin.api().getSession(e.getPlayer());
			if (s != null) {
				s.setLocation(e.getPlayer().getLocation(), e.getMessage().equalsIgnoreCase("//pos1"), false);
			}
		}
	}
	
	@EventHandler
	public void onPhysics(BlockPhysicsEvent e) {
		Location loc = e.getBlock().getLocation();
		for (Entry<UUID, DexSession> entry : plugin.editSessionIter()) {
			DexSession session = entry.getValue();
			if (session.isCancellingPhysics() && loc.getWorld().getName().equals(session.getLocation1().getWorld().getName())) {
				if (loc.getX() >= Math.min(session.getLocation1().getX(), session.getLocation2().getX())
						&& loc.getX() <= Math.max(session.getLocation1().getX(), session.getLocation2().getX())
						&& loc.getY() >= Math.min(session.getLocation1().getY(), session.getLocation2().getY())
						&& loc.getY() <= Math.max(session.getLocation1().getY(), session.getLocation2().getY())
						&& loc.getZ() >= Math.min(session.getLocation1().getZ(), session.getLocation2().getZ())
						&& loc.getZ() <= Math.max(session.getLocation1().getZ(), session.getLocation2().getZ())) {
					e.setCancelled(true);
					return;
				}
			}
		}
	}
	
	private void updateAxes(TransactionEvent e) {
		if (e.getSession().isShowingAxes()) {
			if (e.getTransaction() instanceof RemoveTransaction) e.getSession().setShowingAxes(null);
			else e.getSession().updateAxisDisplays();
		}
	}
	
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent e) {
		plugin.processUnloadedDisplaysInChunk(e.getChunk());
	}
	
	@EventHandler
	public void onTransactionPush(TransactionCompletionEvent e) {
		updateAxes(e);
	}
	
	@EventHandler
	public void onTransactionUndo(TransactionUndoEvent e) {
		updateAxes(e);
	}
	
	@EventHandler
	public void onTransactionRedo(TransactionRedoEvent e) {
		updateAxes(e);
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		UUID u = e.getPlayer().getUniqueId();
		DexSession session = plugin.getEditSession(u);
		if (session != null) {
			session.cancelEdit();
			new BukkitRunnable() {
				@Override
				public void run() {
					Player p = Bukkit.getPlayer(u);
					if (p == null || !p.isOnline()) plugin.deleteEditSession(u);
				}
			}.runTaskLater(plugin, 600l); //TODO make this configurable
		}
	}
}
