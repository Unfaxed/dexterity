package me.c7dev.dexterity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import com.sk89q.worldedit.regions.Region;

import me.c7dev.dexterity.api.DexRotation;
import me.c7dev.dexterity.api.DexterityAPI;
import me.c7dev.dexterity.api.events.SessionSelectionChangeEvent;
import me.c7dev.dexterity.api.events.TransactionCompletionEvent;
import me.c7dev.dexterity.api.events.TransactionRedoEvent;
import me.c7dev.dexterity.api.events.TransactionUndoEvent;
import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.transaction.BlockTransaction;
import me.c7dev.dexterity.transaction.BuildTransaction;
import me.c7dev.dexterity.transaction.RemoveTransaction;
import me.c7dev.dexterity.transaction.Transaction;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.Mask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Holds a player's in-game command state and transaction history
 */
public class DexSession {
	
	public enum EditType {
		TRANSLATE,
		CLONE,
		CLONE_MERGE,
	}
	
	public enum AxisType {
		SCALE,
		ROTATE
	}
	
	private Player p;
	private Location l1, l2;
	private DexterityDisplay selected, secondary;
	private Dexterity plugin;
	private Vector3f editingScale;
	private Vector following, l1ScaleOffset, l2ScaleOffset;
	private EditType editType;
	private Transaction editTransaction;
	private Location origLoc;
	private double volume = 0;
	private ArrayDeque<Transaction> toUndo = new ArrayDeque<>(), toRedo = new ArrayDeque<>(); //push/pop from first element
	private BuildTransaction buildTrans;
	private Mask mask;
	private boolean cancelPhysics = false, sentClickMsg = false;
	private BlockDisplay[] axisX, axisY, axisZ;
	private AxisType showingAxis = null;
	private BukkitRunnable actionbarRunnable;
	
	/**
	 * Initializes a new session for a player
	 * @param player
	 * @param plugin
	 */
	public DexSession(Player player, Dexterity plugin) {
		if (player == null || plugin == null || !player.isOnline()) throw new IllegalArgumentException("Player must be online!");
		p = player;
		this.plugin = plugin;
		plugin.deleteEditSession(player.getUniqueId());
		plugin.setEditSession(player.getUniqueId(), this);
		
		if (plugin.usingWorldEdit()) {
			Region r = plugin.getSelection(player);
			if (r != null) {
				if (r.getMinimumPoint() != null) {
					l1 = DexUtils.location(p.getWorld(), r.getMinimumPoint());
					l1ScaleOffset = new Vector(0, 0, 0);
				}
				if (r.getMaximumPoint() != null) {
					l2 = DexUtils.location(p.getWorld(), r.getMaximumPoint());
					l2ScaleOffset = new Vector(1, 1, 1);
				}
				selectFromLocations();
			}
		}
	}
	
	/**
	 * Retrieves first location set by player
	 * @return
	 */
	public Location getLocation1() {
		return l1;
	}
	/**
	 * Retrieves second location set by player
	 * @return
	 */
	public Location getLocation2() {
		return l2;
	}
	
	public Vector3f getEditingScale() {
		return editingScale;
	}
	public void setEditingScale(Vector3f scale) {
		editingScale = scale;
	}
	
	public Player getPlayer() {
		return p;
	}
	
	public Mask getMask() {
		return mask;
	}
	
	public DexterityDisplay getSelected() {
		return selected;
	}
	
	public DexterityDisplay getSecondary() {
		return secondary;
	}
	
	/**
	 * Gets whether the blocks between the 2 locations have physics updates
	 * @return true if block physics events should be cancelled
	 */
	public boolean isCancellingPhysics() {
		return (hasLocationsSet() && cancelPhysics);
	}
	
	/**
	 * Sets whether the blocks between the 2 locations have physics updates
	 * @param b
	 */
	public void setCancelPhysics(boolean b) {
		cancelPhysics = b;
	}
	
	/**
	 * Changes the player's selection
	 * @param o The new selection, or null for no selection
	 * @param msg true if the player should be notified in chat
	 */
	public void setSelected(DexterityDisplay o, boolean msg) {
		if (o == null) {
			cancelEdit();
			
			SessionSelectionChangeEvent event = new SessionSelectionChangeEvent(this, selected, o);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) return;

			selected = null;
			sentClickMsg = false;
			updateAxisDisplays();
			return;
		}
		
		//check edit lock
		UUID editingLock = null;
		if (selected != null && o != null) editingLock = o.getEditingLock();
		if (editingLock != null) {
			Player editor = Bukkit.getPlayer(editingLock);
			if (editor == null) o.setEditingLock(null);
			else {
				if (editingLock.equals(p.getUniqueId())) p.sendMessage(plugin.getConfigString("must-finish-edit"));
				else p.sendMessage(plugin.getConfigString("cannot-select-with-edit").replaceAll("\\Q%editor%\\E", editor.getName()));
				return;
			}
		}
			
		if (!o.hasOwner(p)) {
			if (msg) p.sendMessage(plugin.getConfigString("no-permission"));
			return;
		}
		
		SessionSelectionChangeEvent event = new SessionSelectionChangeEvent(this, selected, o);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) return;
		
		selected = o;
		sentClickMsg = false;
		updateAxisDisplays();
		if (msg && o.getLabel() != null && p.isOnline()) {
			p.sendMessage(plugin.getConfigString("selected-success").replaceAll("\\Q%label%\\E", o.getLabel()));
			plugin.api().tempHighlight(o, 15);
		}
	}
	
	/**
	 * Returns true if the selection is following the player, such as in a translation edit
	 * @return
	 * @see #startFollowing()
	 * @see #stopFollowing()
	 */
	public boolean isFollowing() {
		return following != null;
	}
	
	public void startFollowing() {
		if (selected == null) return;
		following = selected.getCenter().toVector().subtract(DexUtils.blockLoc(p.getLocation()).toVector());
	}
	
	public void stopFollowing() {
		following = null;
	}
	
	public Vector getFollowingOffset() {
		if (following == null) return null;
		return following.clone();
	}
	public void setFollowingOffset(Vector v) {
		following = v.clone();
	}
	
	/**
	 * Deletes the edit history, removing any undo or redo transactions
	 */
	public void clearHistory() {
		toUndo.clear();
		toRedo.clear();
	}
	
	/**
	 * Adds an edit transaction to the stack
	 * @param t
	 */
	public void pushTransaction(Transaction t) {
		if (!t.isPossible() || t instanceof RemoveTransaction) {
			buildTrans = null;
			toUndo.clear();
		}
		toRedo.clear();
		TransactionCompletionEvent e2 = new TransactionCompletionEvent(this, t);
		
		if (buildTrans != null) {
			if (buildTrans.size() > 0) {
				buildTrans.commit();
				toUndo.addFirst(buildTrans);
				
				TransactionCompletionEvent e1 = new TransactionCompletionEvent(this, buildTrans);
				Bukkit.getPluginManager().callEvent(e1);
			}
			if (t != buildTrans) {
				toUndo.addFirst(t);
				Bukkit.getPluginManager().callEvent(e2);
			}
			buildTrans = null;
		}
		else {
			toUndo.addFirst(t);
			Bukkit.getPluginManager().callEvent(e2);
		}
		trimToSize();
	}
	
	/**
	 * Adds a modified block to the working {@link BuildTransaction}
	 * @param db
	 * @param placing true if player is placing the {@link DexBlock}, false if breaking
	 */
	public void pushBlock(DexBlock db, boolean placing) {
		if (db.getDexterityDisplay() == null) return;
		if (buildTrans == null) buildTrans = new BuildTransaction(db.getDexterityDisplay());
		else if (!db.getDexterityDisplay().getUniqueId().equals(buildTrans.getDisplayUniqueId())) {
			buildTrans.commit();
			pushTransaction(buildTrans);
			buildTrans = new BuildTransaction(db.getDexterityDisplay());
		}
		
		if (placing) buildTrans.addBlock(db);
		else buildTrans.removeBlock(db);
	}
		
	/**
	 * Executes 1 undo
	 */
	public void undo() {
		executeUndo(1);
	}
	
	/**
	 * Executes a number of undo(s)
	 * @param count The number of undos to execute
	 */
	public void undo(int count) {
		if (count < 1) return;
		count = Math.max(Math.min(count, toUndo.size()), 1);
		for (int i = 0; i < count; i++) {
			executeUndo(i == count - 1 ? count : 0);
		}
	}
	
	/**
	 * Executes 1 redo
	 */
	public void redo() {
		executeRedo(1);
	}
	
	/**
	 * Executes a number of redo(s)
	 * @param count The number of redos to execute
	 */
	public void redo(int count) {
		if (count < 1) return;
		count = Math.max(Math.min(count, toRedo.size()), 1);
		for (int i = 0; i < count; i++) {
			executeRedo(i == count - 1 ? count : 0);
		}
	}
	
	private void trimToSize() {
		int max = plugin.getConfig().getInt("session-history-size");
		if (max <= 0) {
			toUndo.clear();
			return;
		}
		int toRemove = toUndo.size() - max;
		for (int i = 0; i < toRemove; i++) toUndo.removeLast();
	}
	
	private void executeUndo(int count) {
		if (buildTrans != null) {
			buildTrans.commit();
			pushTransaction(buildTrans);
			buildTrans = null;
		}
		
		if (toUndo.size() == 0) {
			if (count > 0) p.sendMessage(plugin.getConfigString("none-undo"));
			return;
		}
		
		if (!toUndo.getFirst().isCommitted()) {
			p.sendMessage(plugin.getConfigString("still-processing"));
			return;
		}
		
		Transaction undo = toUndo.removeFirst();
		
		if (!undo.isPossible()) {
			if (count > 0) p.sendMessage(plugin.getConfigString("cannot-undo"));
			return;
		}
		
		toRedo.addFirst(undo);
		DexterityDisplay set = undo.undo();
		
		if (set != null) selected = set;
		
		TransactionUndoEvent event = new TransactionUndoEvent(this, undo);
		Bukkit.getPluginManager().callEvent(event);
		
		if (count > 0) {
			String msg = plugin.getConfigString("undo-success").replaceAll("\\Q%number%\\E", "" + count).replaceAll("\\Q(s)\\E", count == 1 ? "" : "s");
			p.sendMessage(msg);
		}
	}
	
	private void executeRedo(int count) {
		if (toRedo.size() == 0) {
			if (count > 0) p.sendMessage(plugin.getConfigString("none-redo"));
			return;
		}
		
		Transaction redo = toRedo.removeFirst();
		
		if (!redo.isPossible()) {
			if (count > 0) p.sendMessage(plugin.getConfigString("cannot-redo"));
			return;
		}
		
		
		toUndo.addFirst(redo);
		redo.redo();
		
		TransactionRedoEvent event = new TransactionRedoEvent(this, redo);
		Bukkit.getPluginManager().callEvent(event);

		if (count > 0) {
			String msg = plugin.getConfigString("redo-success").replaceAll("\\Q%number%\\E", "" + count).replaceAll("\\Q(s)\\E", count == 1 ? "" : "s");
			p.sendMessage(msg);
		}
	}
	
	/**
	 * Enters the player into an edit session
	 * @param d The new selection to set as primary
	 * @param type
	 * @param swap If true and there exists a selection already, current selection will be reselected after edit session is over
	 */
	public void startEdit(DexterityDisplay d, EditType type, boolean swap) {
		startEdit(d, type, swap, null);
	}
	
	/**
	 * Enters the player into an edit session
	 * @param d The new selection to set as primary
	 * @param type
	 * @param swap If true and there exists a selection already, current selection will be reselected after edit session is over
	 * @param t Transaction to commit any blocks to during edit session
	 */
	public void startEdit(DexterityDisplay d, EditType type, boolean swap, Transaction t) {
		if (selected == null || editType != null) return;
		editType = type;
		editTransaction = t;
		if (d != selected) {
			if (swap) {
				secondary = selected;
				secondary.setEditingLock(p.getUniqueId());
				selected = d;
			} else {
				setSelected(d, false);
			}
		} else selected.setEditingLock(p.getUniqueId());
		origLoc = d.getCenter();
	}
	
	/**
	 * Removes player from any edit session and restore to previous state
	 */
	public void cancelEdit() {
		if (selected == null) return;
		if (secondary != null) {
			selected.remove(false);
			selected = secondary;
			secondary = null;
			selected.setEditingLock(null);
		}
		if (editType == EditType.TRANSLATE && origLoc != null) {
			if (secondary != null) secondary.teleport(origLoc);
			else selected.teleport(origLoc);
		}
		editTransaction = null;
		finishEdit();
	}
	
	/**
	 * Completes the edit session
	 */
	public void finishEdit() {
		if (selected == null) return;
		if (secondary != null) {
			secondary.setEditingLock(null);
			if (editType != EditType.CLONE) selected = secondary;
		}
		secondary = null;
		following = null;
		selected.setEditingLock(null);
		stopFollowing();
		if (editTransaction != null) {
			switch (editType) {
			case TRANSLATE:
				BlockTransaction t = (BlockTransaction) editTransaction;
				t.commit(selected.getBlocks());
				t.commitCenter(selected.getCenter());
			default:
			}
			pushTransaction(editTransaction);
		}
		editType = null;
	}
	
	public EditType getEditType() {
		return editType;
	}
	
	public World getWorld() {
		return l1 == null ? null : l1.getWorld();
	}
	
	/**
	 * Returns the max between number of entities and volume between 2 locations
	 * @return
	 */
	public double getSelectionVolume() {
		return Math.max(getSelectedVolumeSpace(), getSelectedVolumeCount());
	}
	
	/**
	 * Returns true if locations are set and valid.
	 * @return
	 */
	public boolean hasLocationsSet() {
		return (l1 != null && l2 != null && l1.getWorld().getName().equals(l2.getWorld().getName()));
	}
	
	/**
	 * Simple function to send the default click message for a saved display
	 */
	public void clickMsg() {
		if (sentClickMsg) return;
		sentClickMsg = true;
		p.sendMessage(plugin.getConfigString("saved-click-default"));
	}
	
	/**
	 * Returns the number of blocks of volume between 2 locations cuboid
	 * @return
	 */
	public double getSelectedVolumeSpace() {
		if (hasLocationsSet()) return volume;
		else return 0;
	}
	
	/**
	 * Returns the largest volume defined by the dexterity.maxvolume.# permission
	 * @return Min of configured max volume and volume from permissions
	 */
	public double getPermittedVolume() {
		for (PermissionAttachmentInfo perm : p.getEffectivePermissions()) {
			if (perm.getPermission().startsWith("dexterity.maxvolume.")) {
				try {
					double r = Double.parseDouble(perm.getPermission().replaceAll("dexterity\\.maxvolume\\.", ""));
					if (r < plugin.getMaxVolume()) return r;
				} catch (Exception ex) {
				}
				break;
			}
		}
		return plugin.getMaxVolume();
	}
	
	/**
	 * Returns the number of {@link DexBlock} in the selection
	 * @return Integer count of blocks or 0 if nothing selected
	 */
	public double getSelectedVolumeCount() {
		return selected == null ? 0 : selected.getBlocks().length;
	}
	
	/**
	 * Sets the first or second location to a block
	 * @param loc
	 * @param isL1 true if setting the first location
	 */
	public void setLocation(Location loc, boolean isL1) {
		setLocation(loc, isL1, true);
	}
	
	/**
	 * Sets the first or second location to a block
	 * @param loc
	 * @param isL1 true if setting the first location
	 * @param verbose true if player should be notified in chat
	 */
	public void setLocation(Location loc, boolean isL1, boolean verbose) {
		setContinuousLocation(DexUtils.blockLoc(loc), isL1, isL1 ? new Vector(0, 0, 0) : new Vector(1, 1, 1), verbose);
		if (hasLocationsSet()) volume = DexUtils.getBlockVolume(l1, l2);
	}
	
	/**
	 * Precisely sets the first or second location
	 * @param loc
	 * @param isL1 true if setting the first location
	 * @param scaleOffset The offset added to the minimum or maximum coordinate once both locations are set
	 * @param verbose true if player should be notified in chat
	 */
	public void setContinuousLocation(Location loc, boolean isL1, Vector scaleOffset, boolean verbose) {
		
//		if (scale == null) DexUtils.blockLoc(loc);
//		else scale.multiply(0.5);

		
		if (isL1) {
			l1 = loc;
			l1ScaleOffset = scaleOffset.clone().multiply(0.5);
		}
		else {
			l2 = loc;
			l2ScaleOffset = scaleOffset.clone().multiply(0.5);
		}
		
		selectFromLocations();
		
		if (verbose) p.sendMessage(plugin.getConfigString("set-success").replaceAll("\\Q%number%\\E", isL1 ? "1" : "2").replaceAll("\\Q%location%\\E", DexUtils.locationString(loc, 0)));
	}

	private void selectFromLocations() {
		if (hasLocationsSet() && editType == null) {
			volume = DexUtils.getVolume(l1.clone().add(l1ScaleOffset), l2.clone().add(l2ScaleOffset));
			if (volume > Math.min(plugin.getMaxVolume(), getPermittedVolume())) {
				setSelected(null, false);
				return;
			}
			DexterityDisplay d = plugin.api().selectFromLocations(l1, l2, mask, l1ScaleOffset, l2ScaleOffset);
			if (d == null) setSelected(null, false);
			else {
				SessionSelectionChangeEvent event = new SessionSelectionChangeEvent(this, selected, d);
				Bukkit.getPluginManager().callEvent(event);
				if (event.isCancelled()) return;

				highlightSelected(d);
				selected = d;
				updateAxisDisplays();
			}
		} else volume = 0;
	}
	
	public void setShowingAxes(AxisType a) {
		showingAxis = a;
		updateAxisDisplays();
	}
	
	public boolean isShowingAxes() {
		return showingAxis != null;
	}
	
	public AxisType getShowingAxisType() {
		return showingAxis;
	}
	
	public void updateAxisDisplays() {
		removeAxes();
		if (selected == null || showingAxis == null) return;
		
		DexRotation rot = selected.getRotationManager(true);
		DexterityAPI api = plugin.api();
		Location c = selected.getCenter();
		
		switch (showingAxis) {
		case SCALE:
			Vector scale = selected.getScale();
			axisX = api.markerVector(c, c.clone().add(rot.getXAxis().multiply(scale.getX())), Material.RED_CONCRETE, Color.RED, -1);
			axisY = api.markerVector(c, c.clone().add(rot.getYAxis().multiply(scale.getY())), Material.LIME_CONCRETE, Color.LIME, -1);
			axisZ = api.markerVector(c, c.clone().add(rot.getZAxis().multiply(scale.getZ())), Material.BLUE_CONCRETE, Color.BLUE, -1);
			break;
		case ROTATE:
			axisX = api.markerVector(c, c.clone().add(rot.getXAxis()), Material.RED_CONCRETE, Color.RED, -1);
			axisY = api.markerVector(c, c.clone().add(rot.getYAxis()), Material.LIME_CONCRETE, Color.LIME, -1);
			axisZ = api.markerVector(c, c.clone().add(rot.getZAxis()), Material.BLUE_CONCRETE, Color.BLUE, -1);
			break;
		default:
		}
	}
	
	public void removeAxes() {
		DexterityAPI api = plugin.api();
		if (axisX != null) api.removeMarker(axisX);
		if (axisY != null) api.removeMarker(axisY);
		if (axisZ != null) api.removeMarker(axisZ);
		axisX = null;
		axisY = null;
		axisZ = null;
	}
	
	private void highlightSelected(DexterityDisplay newDisp) {
		if (selected != null) plugin.api().unTempHighlight(selected);
		plugin.api().tempHighlight(newDisp, 30);
	}
	
	public void setMask(Mask mask) {
		this.mask = mask;
		selectFromLocations();
		
		if (actionbarRunnable != null && !actionbarRunnable.isCancelled()) {
			actionbarRunnable.cancel();
			actionbarRunnable = null;
			p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b"));
		}
		
		if (mask != null) {
			if (selected != null && !selected.isSaved()) {
				DexterityDisplay s = new DexterityDisplay(plugin, selected.getCenter(), selected.getScale());
				List<DexBlock> dblocks = new ArrayList<>();
				for (DexBlock db : selected.getBlocks()) {
					if (mask.isAllowed(db.getEntity().getBlock().getMaterial())) dblocks.add(db);
				}
				if (dblocks.size() == s.getBlocks().length) selectFromLocations();

				if (dblocks.size() == 0) setSelected(null, false);
				else {
					s.setBlocks(dblocks, true);
					highlightSelected(s);
					setSelected(s, false);
				}
			}
			
			String maskNum = (mask.isNegative() ? "(-) " : "") + mask.getBlocks().size();
			String abMsg = plugin.getConfigString("mask-enabled")
					.replaceAll("\\Q%material_count%\\E", maskNum)
					.replaceAll("\\Q(s)\\E", mask.getBlocks().size() == 1 ? "" : "s");
			
			if (abMsg.length() > 0) {
				actionbarRunnable = new BukkitRunnable() {
					@Override
					public void run() {
						if (!p.isOnline()) {
							this.cancel();
							actionbarRunnable = null;
						}
						else p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(abMsg));
					}
				};
				actionbarRunnable.runTaskTimer(plugin, 0, 20l);
			}
		}
	}
	
	public void clearLocationSelection() {
		l1 = null;
		l2 = null;
	}
	
}
