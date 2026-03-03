package me.c7dev.dexterity.displays;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import me.c7dev.dexterity.Dexterity;
import me.c7dev.dexterity.api.DexRotation;
import me.c7dev.dexterity.api.events.DisplayTranslationEvent;
import me.c7dev.dexterity.displays.animation.Animation;
import me.c7dev.dexterity.displays.animation.RideableAnimation;
import me.c7dev.dexterity.displays.schematics.SchematicBuilder;
import me.c7dev.dexterity.transaction.BlockTransaction;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.DexterityException;
import me.c7dev.dexterity.util.InteractionCommand;
import me.c7dev.dexterity.util.Mask;
import me.c7dev.dexterity.util.OrientationKey;
import me.c7dev.dexterity.util.RollOffset;
import me.c7dev.dexterity.util.RotationPlan;

/**
 * Defines a selection of {@link DexBlock}, possibly with a saved display label or sub-displays
 */
public class DexterityDisplay {
	
	private Dexterity plugin;
	private Location center;
	private String label, itemSchemLabel;
	private Vector scale;
	private DexterityDisplay parent;
	private boolean startedAnimations = false, zeroPitch = false, listed = true;
	private UUID uuid = UUID.randomUUID(), editingLock;
	private DexRotation rot = null;
	private ItemStack item;
	
	private List<DexBlock> blocks = new ArrayList<>();
	private List<Animation> animations = new ArrayList<>();
	private List<DexterityDisplay> subdisplays = new ArrayList<>();
	private List<InteractionCommand> cmds = new ArrayList<>();
	private List<UUID> owners = new ArrayList<>();
	
	/**
	 * Initializes an empty selection.
	 * @param plugin
	 */
	public DexterityDisplay(Dexterity plugin) {
		this(plugin, null, new Vector(1, 1, 1), null);
	}
	
	/**
	 * Initializes a selection
	 * @param plugin
	 * @param center
	 * @param scale Vector with the regular block size being [1, 1, 1]
	 */
	public DexterityDisplay(Dexterity plugin, Location center, Vector scale) {
		this(plugin, center, scale, null);
	}

	/**
	 * Initializes a new saved display with a unique label
	 * @param plugin
	 * @param center
	 * @param scale Vector with the regular block size being [1, 1, 1]
	 * @param label
	 */
	public DexterityDisplay(Dexterity plugin, Location center, Vector scale, String label) {
		this(plugin, center, scale, label, null);
	}
	
	/**
	 * Initializes a new saved display with a unique label
	 * @param plugin
	 * @param center
	 * @param scale Vector with the regular block size being [1, 1, 1]
	 * @param label
	 * @param owners
	 */
	public DexterityDisplay(Dexterity plugin, Location center, Vector scale, String label, List<OfflinePlayer> owners) {
		this.plugin = plugin;
		this.scale = scale == null ? new Vector(1, 1, 1) : scale;
		if (center == null) recalculateCenter();
		else this.center = center;
		if (label != null) setLabel(label);
		setOwners(owners);
	}
	
	public UUID getUniqueId() {
		return uuid;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DexterityDisplay)) return false;
		DexterityDisplay d = (DexterityDisplay) o;
		return uuid.equals(d.getUniqueId());
	}
	
	/**
	 * Retrieves the label if one is set
	 * @return Unmodifiable label string if selection is saved, otherwise null
	 */
	public String getLabel() {
		return label;
	}
	
	/**
	 * Set to false to hide display from /d list. All saved displays are listed by default
	 * @param b
	 */
	public void setListed(boolean b) {
		listed = b;
		for (DexterityDisplay d : subdisplays) d.setListed(b);
	}
	
	/**
	 * Checks if display is listed on normal list
	 * @return true if display will show on the saved display list (/d list), or false if display is only intended to be temporary.
	 */
	public boolean isListed() {
		return listed && label != null;
	}
	
	public double getYaw() {
		return rot == null ? 0 : rot.getYaw();
	}
	
	public double getPitch() {
		return rot == null ? 0 : rot.getPitch();
	}
	
	public double getRoll() {
		return rot == null ? 0 : rot.getRoll();
	}
	
	/**
	 * Returns an array of the player(s) that own this display
	 * @return
	 */
	public OfflinePlayer[] getOwners() {
		if (owners == null) return new OfflinePlayer[0];
		OfflinePlayer[] r = new OfflinePlayer[owners.size()];
		for (int i = 0; i < owners.size(); i++) r[i] = Bukkit.getOfflinePlayer(owners.get(i));
		return r;
	}
	
	/**
	 * Sets the players that own this display
	 * @param newOwners
	 */
	public void setOwners(List<OfflinePlayer> newOwners) {
		owners = new ArrayList<>();
		if (newOwners == null) return;
		for (OfflinePlayer o : newOwners) owners.add(o.getUniqueId());
	}
	
	/**
	 * Returns true if the player is one of the owners OR if the player has permission dexterity.select.unowned
	 * @param p
	 * @return
	 */
	public boolean hasOwner(Player p) {
		if (owners == null || owners.size() == 0 || p.hasPermission("dexterity.select.unowned")) return true;
		for (UUID owner : owners) if (owner.equals(p.getUniqueId())) return true;
		return false;
	}
	
	/**
	 * Appends a player who can edit this display
	 * @param p
	 */
	public void addOwner(OfflinePlayer p) {
		if (p == null || owners.contains(p.getUniqueId())) return;
		owners.add(p.getUniqueId());
	}
	
	/**
	 * Removes a player from the owners list
	 * @param p
	 */
	public void removeOwner(OfflinePlayer p) {
		if (p == null) return;
		owners.remove(p.getUniqueId());
	}
	
	/**
	 * Recalculates the average block display location and the overall selection's scale.
	 */
	public void recalculateCenter() {
		recalculateCenter(true);
	}
	
	/**
	 * Recalculates the average block display location and optionally recalculates the overall selection's scale
	 * @param rescale If false, does not change scale
	 */
	public void recalculateCenter(boolean rescale) {
		Vector cvec = new Vector(0, 0, 0);
		World w;
		int n = 0;
		zeroPitch = true;
		
		if (blocks.size() == 0) {
			w = plugin.getDefaultWorld();
			n = 1;
		}
		else {
			w = blocks.get(0).getEntity().getLocation().getWorld();
			n = blocks.size();
			double scalex = -1, scaley = -1, scalez = -1;
			for (DexBlock db : blocks) {
				cvec.add(db.getLocation().toVector());
				
				if (rescale) {
					Vector scale = db.getTransformation().getScale();
					if (scale.getX() > scalex) scalex = scale.getX();
					if (scale.getY() > scaley) scaley = scale.getY();
					if (scale.getZ() > scalez) scalez = scale.getZ();
				}
				
				if (zeroPitch && db.getEntity().getLocation().getPitch() != 0) zeroPitch = false;
			}
			if (rescale) scale = new Vector(scalex, scaley, scalez);
		}
		center = DexUtils.location(w, cvec.multiply(1.0/n));
	}
	
	/**
	 * Saves the display with the ascending default label ('display-#')
	 */
	public void setDefaultLabel() {
		int i =1;
		while (plugin.getDisplayLabels().contains("display-" + i)) i++;
		setLabel("display-" + i);
	}
	
	/**
	 * Saves the selection and turns it into a display by giving it a label
	 * 
	 * @param s Unused label to save the display, or null to unsave it.
	 * @return true if label is unique and the display is saved successfully
	 * @see #unregister()
	 */
	public boolean setLabel(String s) {
		if (s == null) {
			unregister();
			return true;
		}
		s = s.toLowerCase();
		if (s.startsWith("-") || s.contains(".") || s.contains(" ")) throw new IllegalArgumentException("Invalid label! Cannot start with '-' or contain '.' or ' ' symbols.");

		if (plugin.getDisplayLabels().contains(s)) return false;
		if (label != null) plugin.unregisterDisplay(this);
		label = s;
		plugin.registerDisplay(s, this);
		for (DexBlock db : blocks) {
			if (db.getDexterityDisplay() == null || !db.getDexterityDisplay().isSaved()) db.setDexterityDisplay(this);
		}
		updateDropItemMeta();
		return true;
	}
		
	/**
	 * Checks if selection is a saved display or not
	 * @return true if the display has a label, otherwise it is only a selection
	 */
	public boolean isSaved() {
		return label != null;
	}
	
	/**
	 * Unsaves the display and turns it into a regular selection
	 */
	public void unregister() {
		if (!isSaved()) return;
		plugin.unregisterDisplay(this, false);
		label = null;
		for (DexterityDisplay sub : subdisplays) sub.unregister();
	}
	
	/**
	 * Retrieves a list of {@link DexBlock} that can be iterated over
	 * @return Unmodifiable array of DexBlocks
	 * @see #addBlock(DexBlock)
	 * @see #removeBlock(DexBlock)
	 */
	public DexBlock[] getBlocks() {
		return blocks.toArray(new DexBlock[blocks.size()]);
	}
	
	/**
	 * @return The integer number of DexBlocks within the selection
	 */
	public int getBlocksCount() {
		return blocks.size();
	}
	
	/**
	 * Adds a DexBlock to the selection. If the DexBlock is not previously registered to a saved display, it will be registered to this
	 * @param db
	 * @see #removeBlock(DexBlock)
	 */
	public void addBlock(DexBlock db) {
		if (db.getDexterityDisplay() != null && db.getDexterityDisplay().isSaved() && !uuid.equals(db.getDexterityDisplay().getUniqueId())) db.getDexterityDisplay().removeBlockNoUnmap(db);
		db.setDexterityDisplay(this);
		blocks.add(db);
	}

	private void removeBlockNoUnmap(DexBlock db) {
		blocks.remove(db);
	}
	
	/**
	 * Removes a DexBlock to the selection. The DexBlock must be killed or registered to another display.
	 * @param db
	 * @see #addBlock(DexBlock)
	 * @see DexBlock#remove()
	 */
	public void removeBlock(DexBlock db) {
		if (blocks.remove(db)) {
			plugin.clearMappedDisplay(db);
		}
	}
	
	/**
	 * @return The integer number of animations registered, active or not.
	 * @see #addAnimation(Animation)
	 */
	public int getAnimationsCount() {
		return animations.size();
	}
	
	/**
	 * Registers a new animation type, as long as no animation type was already registered to this display
	 * @param a
	 * @see #getAnimation(Class)
	 * @see #removeAnimation(Animation)
	 */
	public void addAnimation(Animation a) {
		if (a == null) return;
		Animation existing = getAnimation(a.getClass());
		if (existing == null && a instanceof RideableAnimation) existing = getAnimation(RideableAnimation.class);
		if (existing != null) {
			Bukkit.getLogger().warning("Dex API: Adding a " + a.getClass().getSimpleName() + " animation that replaces an existing animation!");
			removeAnimation(existing);
		}
		animations.add(a);
	}
	
	/**
	 * Unregisters an animation from display
	 * @param a
	 * @see #getAnimation(Class)
	 * @see #addAnimation(Animation)
	 */
	public void removeAnimation(Animation a) {
		a.kill();
		a.stop();
		animations.remove(a);
	}
	
	/**
	 * Retrieves a registered animation type if it exists
	 * @param clazz
	 * @return null if no animation of this type has been registered
	 * @see #addAnimation(Animation)
	 */
	public Animation getAnimation(Class<?> clazz) { //animations list should hold unique animation types
		for (Animation a : animations) {
			if (clazz.isInterface()) {
				if (clazz.isInstance(a)) return a;
			}
			else if (a.getClass() == clazz) return a;
		}
		return null;
	}
	
	/**
	 * Retrieves a list of commands to run when display is clicked
	 * 
	 * @return Unmodifiable array of InteractionCommand cmd data
	 */
	public InteractionCommand[] getCommands() {
		return cmds.toArray(new InteractionCommand[cmds.size()]);
	}
	
	/**
	 * Retrieves the size of the command list
	 * @return size of array
	 */
	public int getCommandCount() {
		return cmds.size();
	}
	
	/**
	 * Adds a command to run when display is clicked
	 * @param cmd
	 */
	public void addCommand(InteractionCommand cmd) {
		if (!cmds.contains(cmd)) cmds.add(cmd);
	}
	
	/**
	 * Removes a command from being run when display is clicked
	 * @param cmd
	 */
	public void removeCommand(InteractionCommand cmd) {
		cmds.remove(cmd);
	}
	
	/**
	 * Retrieves the vector representing the overall scale of the display
	 * @return
	 */
	public Vector getScale() {
		return scale.clone();
	}
	
	/**
	 * Sets the item that will be dropped if a player breaks the display
	 * @param item
	 */
	public void setDropItem(ItemStack item, String schemName) {
		if (!isSaved()) throw new RuntimeException("Can not set display's drop item when display is not saved.");
		if (item == null) {
			if (itemSchemLabel != null) {
				File f = new File(plugin.getDataFolder().getAbsolutePath() + "/schematics/" + itemSchemLabel);
				if (f.exists()) {
					try {
						f.delete();
					} catch (Exception ex) {}
				}
			}
			itemSchemLabel = null;
		}
		else {
			if (item.getType() == Material.AIR) {
				this.item = null;
				return;
			}
			
			//save new schem if not exists
			itemSchemLabel = schemName;
			File schem_f = new File(plugin.getDataFolder().getAbsolutePath() + "/schematics/" + schemName + ".dexterity");
			if (!schem_f.exists()) {
				SchematicBuilder s = new SchematicBuilder(plugin, this);
				s.save(schemName, "CONSOLE", true);
			}
		}
		this.item = item.clone();
		updateDropItemMeta();
	}
	
	public String getDropItemSchematicName() {
		return itemSchemLabel;
	}
	
	private void updateDropItemMeta() {
		if (item == null || itemSchemLabel == null || label == null) return;
		NamespacedKey key = new NamespacedKey(plugin, "dex-schem-label");
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, itemSchemLabel);
		
		String disp_label = DexUtils.getEffectiveLabel(label);
		
		meta.setDisplayName("§f" + disp_label.toUpperCase().substring(0, 1) + disp_label.substring(1));
		item.setItemMeta(meta);
	}
	
	/**
	 * Gets a copy of the item that will be dropped when the player breaks the display
	 * @return
	 */
	public ItemStack getDropItem() {
		return item == null ? null : item.clone();
	}
	
	/**
	 * Remove the display but drop it as an item if an item has been defined
	 */
	public void dropNaturally() {
		if (item != null) {
			Item e = center.getWorld().dropItem(center, item);
			Vector v = e.getVelocity();
			v.setX(v.getX() * 0.2);
			v.setZ(v.getZ() * 0.2);
			e.setVelocity(v);
		}
		remove();
	}
	
	/**
	 * Overrides the blocks in this display
	 * @param entities A list of unique DexBlocks
	 * @param recalcCenter Recalculates the center point and scale if true
	 * @see #getBlocks()
	 */
	public void setBlocks(List<DexBlock> entities, boolean recalcCenter){
		this.blocks = entities;
		plugin.unregisterDisplay(this);
		if (recalcCenter) recalculateCenter();
	}
	
	/**
	 * @return Unmodifiable array of sub-displays registered to this
	 */
	public DexterityDisplay[] getSubdisplays() {
		return subdisplays.toArray(new DexterityDisplay[subdisplays.size()]);
	}
	
	public int getSubdisplayCount() {
		return subdisplays.size();
	}
	
	public void addSubdisplay(DexterityDisplay d) {
		if (!this.isSaved() || !d.isSaved()) throw new IllegalArgumentException("Both displays must be saved to add a subdisplay!");
		if (d.getParent() != null) d.getParent().removeSubdisplay(d);
		subdisplays.add(d);
	}
	
	public void removeSubdisplay(DexterityDisplay d) {
		if (subdisplays.contains(d)) {
			d.setParent(null);
			subdisplays.remove(d);
		}
	}
	
	/**
	 * Retrieves the parent display if this is a child display
	 * @return The parent display if defined
	 */
	public DexterityDisplay getParent() {
		return parent;
	}
	
	/**
	 * Sets the parent display without affecting any child displays
	 * @param p parent display
	 */
	public void setParent(DexterityDisplay p) {
		if (p == this) {
			Bukkit.getLogger().severe("Tried to set parent to self");
			return;
		}
		parent = p;
	}
	
	/**
	 * Retrieves the root node of the sub-display tree
	 * @return the root display node
	 */
	public DexterityDisplay getRootDisplay() {
		return rootDisplay(this);
	}
	
	/**
	 * Retrieves the root node of the sub-display tree of a display
	 * @param d
	 * @return the root display node
	 */
	public static DexterityDisplay rootDisplay(DexterityDisplay d) {
		if (d.getParent() == null) return d;
		else return rootDisplay(d.getParent());
	}
	
	/**
	 * Determines if passed in display is this or any descendant display node
	 * @param d
	 * @return true if display passed in is a child or descendant of this
	 */
	public boolean containsSubdisplay(DexterityDisplay d) {
		if (subdisplays.contains(d)) return true;
		for (DexterityDisplay child : subdisplays) {
			if (child.containsSubdisplay(d)) return true;
		}
		return false;
	}
	
	/**
	 * Checks whether it is appropriate to merge this display with another to form one display
	 * @return true if can hard-merge
	 */
	public boolean canHardMerge() {
		return !hasStartedAnimations();
	}
	
	/**
	 * Checks whether there are running animations on this display
	 * @return true if there are animations running
	 */
	public boolean hasStartedAnimations() {
		return startedAnimations;
	}
	
	/**
	 * Checks whether the rotation is simple enough to not involve pitch or roll
	 * @return true if only yaw is involved
	 */
	public boolean isYawOnly() { //if yaw-only rotation optimization is appropriate
		return zeroPitch;
	}
	
	@Deprecated
	public void setZeroPitch(boolean b) {
		zeroPitch = b;
	}
	
	/**
	 * Merges the DexBlocks in the display into this display
	 * @param subdisplay The display that will be unregistered and whose blocks will be merged
	 * @return true if operation is successful
	 */
	public boolean hardMerge(DexterityDisplay subdisplay) {
		if (!subdisplay.getCenter().getWorld().getName().equals(center.getWorld().getName()) ||
			!subdisplay.canHardMerge() || !canHardMerge()) return false;
		plugin.unregisterDisplay(subdisplay);
		for (DexBlock b : subdisplay.getBlocks()) {
			b.setDexterityDisplay(this);
			blocks.add(b);
			if (zeroPitch && b.getEntity().getLocation().getPitch() != 0) zeroPitch = false;
		}
		for (DexterityDisplay subdisp : subdisplay.getSubdisplays()) {
			subdisp.merge(this, null);
		}
		return true;
	}
	
	/**
	 * Make this display become a child node of either a new display saved as new_group, or a child display of newparent
	 * @param newParent The display that will either be a brother node or parent node depending on if a new parent display is created
	 * @param newGroup Label of the new parent display, or null for no new parent display
	 * @return The parent display after the merge operation
	 */
	public DexterityDisplay merge(DexterityDisplay newParent, String newGroup) {
		if (newParent == this || newParent.getLabel().equals(label) || subdisplays.contains(newParent) || parent != null) throw new IllegalArgumentException("Cannot merge with self!");
		if (rootDisplay(this).containsSubdisplay(newParent)) throw new IllegalArgumentException("One display must be a root node to be able to merge!");
		if (!newParent.getCenter().getWorld().getName().equals(center.getWorld().getName())) throw new IllegalArgumentException("Both displays must be in the same world!");
		if (newGroup != null && plugin.getDisplayLabels().contains(newGroup)) throw new IllegalArgumentException("New group label is already in use!");
		if (!isSaved() || !newParent.isSaved()) throw new IllegalArgumentException("Both displays must be saved!");
		
		plugin.unregisterDisplay(this, true);
		stopAnimations(true);
		newParent.stopAnimations(true);
		Vector c2v = center.toVector().add(newParent.getCenter().toVector()).multiply(0.5); //midpoint
		Location c2 = new Location(center.getWorld(), c2v.getX(), c2v.getY(), c2v.getZ());
		newParent.setCenter(c2);
		
		DexterityDisplay r;
		if (newGroup == null) {
			newParent.addSubdisplay(this);
			setParent(newParent);
			r = newParent;
		} else {
			plugin.unregisterDisplay(newParent, true);
			DexterityDisplay p = new DexterityDisplay(plugin);
			p.setLabel(newGroup);
			
			setParent(p);
			newParent.setParent(p);
			p.addSubdisplay(this);
			p.addSubdisplay(newParent);
			r = p;
		}
		
		plugin.saveDisplay(this);
		return r;
	}
	
	/**
	 * Retrieve the UUID of the offline player who has this display in editing lock
	 * @return UUID of the offline player if in editing lock, otherwise null
	 */
	public UUID getEditingLock() {
		return editingLock;
	}
	
	/**
	 * Lock this display so that it cannot be selected by any other player
	 * @param u The UUID of the player who has this display locked to them
	 */
	public void setEditingLock(UUID u) {
		editingLock = u;
	}
	
	/**
	 * Remove this display from its parent node
	 */
	public void unmerge() {
		if (parent == null) return;
		parent.removeSubdisplay(this);
		parent = null;
		plugin.registerDisplay(label, this);
	}
	
	/**
	 * Delete this display and sub-displays from the world
	 * @param restore true if the DexBlocks are to be deconverted into regular blocks
	 */
	public void remove(boolean restore) {
		if (parent != null) parent.removeSubdisplay(this);
		removeHelper(restore);
	}
	
	/**
	 * Delete this display and sub-displays from the world
	 */
	public void remove() {
		remove(false);
	}
		
	private void removeHelper(boolean restore) {
		if (restore) {
			for (DexBlock b : blocks) {
				Location loc = DexUtils.blockLoc(b.getEntity().getLocation());
				loc.getBlock().setBlockData(b.getEntity().getBlock());
				b.getEntity().remove();
			}
		} else for (DexBlock b : blocks) b.getEntity().remove();
		
		plugin.unregisterDisplay(this);
		
		for (DexterityDisplay subdisplay : subdisplays.toArray(new DexterityDisplay[subdisplays.size()])) subdisplay.remove(restore);
	}
	
	/**
	 * Gets the total count of nodes in the sub-display tree, including this node
	 * @return 
	 */
	public int getGroupSize() {
		if (label == null) return 1;
		return getGroupSize(this);
	}
	
	private int getGroupSize(DexterityDisplay s) {
		int i = 1;
		for (DexterityDisplay sub : s.getSubdisplays()) i += getGroupSize(sub);
		return i;
	}
	
	/**
	 * Gets the instance of the plugin
	 * @return
	 */
	public Dexterity getPlugin() {
		return plugin;
	}
	
	/**
	 * Retrieves the center of the display
	 * @return
	 */
	public Location getCenter() {
		return center.clone();
	}
	
	public World getWorld() {
		return center.getWorld();
	}
	
	/**
	 * Sets the center of the display
	 * @param loc
	 */
	public void setCenter(Location loc) {
		if (center != null && !loc.getWorld().getName().equals(center.getWorld().getName())) throw new IllegalArgumentException("Cannot recenter into a different world!");
		center = loc.clone();
	}
	
	/**
	 * Starts all registered animation types for this display
	 */
	public void startAnimations() {
		if (hasStartedAnimations()) return;
		startedAnimations = true;
		for (Animation a : animations) {
			a.start();
		}
	}
	
	/**
	 * Stops or kills all registered animations for this display
	 * @param force true if the animations should be killed instead of allowed to stop
	 */
	public void stopAnimations(boolean force) {
		startedAnimations = false;
		for (Animation a : animations) {
			if (force) a.kill();
			else a.stop();
		}
	}
	
	/**
	 * Moves the display or transfers it to another world
	 * @param loc
	 */
	public void teleport(Location loc) {
		
		Location from = center.clone();
		
		if (loc.getWorld().getName().equals(center.getWorld().getName())) {
			Vector diff = new Vector(loc.getX() - center.getX(), loc.getY() - center.getY(), loc.getZ() - center.getZ());
			teleport(diff);
		} else {
			for (DexBlock db : blocks) {
				Vector diff = new Vector(db.getEntity().getLocation().getX() - center.getX(), db.getEntity().getLocation().getY() - center.getY(), db.getEntity().getLocation().getZ() - center.getZ());
				
				float yaw = db.getEntity().getLocation().getYaw(), pitch = db.getEntity().getLocation().getPitch();
				db.getEntity().teleport(loc.clone().add(diff));
				db.getEntity().setRotation(yaw, pitch);
			}
			center = loc.clone();
			for (DexterityDisplay subd : subdisplays) subd.teleport(loc);
		}
		
		DisplayTranslationEvent event = new DisplayTranslationEvent(this, from, loc);
		Bukkit.getPluginManager().callEvent(event);
	}
	
	/**
	 * Moves the display by an offset
	 * @param diff
	 */
	public void teleport(Vector diff) {
		center.add(diff);
		for (DexBlock b : blocks) {
			b.move(diff);
		}
		for (DexterityDisplay subd : subdisplays) subd.teleport(diff);
	}
	
	/**
	 * Sets the glow color of the blocks in this display
	 * @param c The color of the glow
	 * @param propegate true if descendant displays should receive this update
	 */
	public void setGlow(Color c, boolean propegate) {
		if (c == null) {
			for (DexBlock b : blocks) b.getEntity().setGlowing(false);
		} else {
			for (DexBlock b : blocks) {
				b.getEntity().setGlowColorOverride(c);
				b.getEntity().setGlowing(true);
			}
		}
		if (propegate) {
			for (DexterityDisplay d : subdisplays) d.setGlow(c, true);
		}
	}
	
	/**
	 * Scale by a multiplier
	 * @param s
	 */
	public void scale(double s) {
		if (s == 0) throw new IllegalArgumentException("Scale cannot be zero!");
		Vector centerv = center.toVector();
		for (DexBlock db : blocks) {
			
			Vector diff = db.getLocation().toVector().subtract(centerv).multiply(s-1);
			Vector block_scale = db.getTransformation().getScale().multiply(s);
			
			db.move(diff);
			
			db.getTransformation()
					.setDisplacement(block_scale.clone().multiply(-0.5))
					.setScale(block_scale);
			if (db.getTransformation().getRollOffset() != null) db.getTransformation().getRollOffset().multiply(s);
			db.updateTransformation();
		}
		scale = scale.multiply(s);
		for (DexterityDisplay sub : subdisplays) sub.setScale(s);
	}
	
	/**
	 * Set the scale
	 * @param s Representing x, y, and z scale
	 */
	public void setScale(double s) {
		setScale(new Vector(s, s, s));
	}
	
	/**
	 * Set the skew for x, y, and z, respectively
	 * @param s
	 */
	public void setScale(Vector s) {
		if (s.getX() == 0 || s.getY() == 0 || s.getZ() == 0) return;
		scale(new Vector(s.getX() / scale.getX(), s.getY() / scale.getY(), s.getZ() / scale.getZ()));
	}
		
	/**
	 * Skew by a multiplier along x, y, and z, respectively
	 * @param v
	 * @throws DexterityException if skewing a selection with more than 1 rotation orientation, as it is impossible to create parallelograms
	 */
	public void scale(Vector v) {
		if (blocks.size() == 0) return;
		if (v.getX() == 0 || v.getY() == 0 || v.getZ() == 0) throw new IllegalArgumentException("Scale cannot be zero!");
		if (v.getX() == v.getY() && v.getY() == v.getZ()) {
			scale(v.getX()); //much faster calculation with scalar
			return;
		}
		
		OrientationKey all_key = null;
		for (DexBlock db : blocks) {
			OrientationKey key = new OrientationKey(db.getLocation().getYaw(), db.getLocation().getPitch(), db.getTransformation().getLeftRotation());
			if (all_key == null) all_key = key;
			else if (!all_key.equals(key)) throw new DexterityException("This selection has too many rotation orientation types to skew!");
		}
		
		HashMap<Vector, RollOffset> offsets = new HashMap<>();
		
		if (rot == null) rot = new DexRotation(this);
		Matrix3d unitVecs = new Matrix3d(
				rot.getXAxis().getX(), rot.getXAxis().getY(), rot.getXAxis().getZ(),
				rot.getYAxis().getX(), rot.getYAxis().getY(), rot.getYAxis().getZ(),
				rot.getZAxis().getX(), rot.getZAxis().getY(), rot.getZAxis().getZ()
				);
		unitVecs.invert();
		Vector x1 = new Vector(unitVecs.m00, unitVecs.m10, unitVecs.m20);
		Vector y1 = new Vector(unitVecs.m01, unitVecs.m11, unitVecs.m21);
		Vector z1 = new Vector(unitVecs.m02, unitVecs.m12, unitVecs.m22);
		
		Vector centerv = center.toVector();
		for (DexBlock db : blocks) {
			Vector diff = db.getLocation().toVector().subtract(centerv);
			Vector unit_composition = DexUtils.vector(unitVecs.transform(DexUtils.vector(diff))); //gets displacement in terms of unitVecs
			Vector unit_diff = DexUtils.hadimard(unit_composition, v);
			
			Vector scaled_diff = DexUtils.linearCombination(x1, y1, z1, unit_diff);
			
			db.move(scaled_diff.subtract(diff));
			Vector block_scale = DexUtils.hadimard(db.getTransformation().getScale(), v);
			
			RollOffset ro = offsets.get(block_scale);
			if (ro == null) {
				ro = new RollOffset(db.getRoll(), block_scale);
				offsets.put(block_scale, ro);
			}
			
			db.getTransformation()
					.setDisplacement(block_scale.clone().multiply(-0.5))
					.setScale(block_scale)
					.setRollOffset(ro.getOffset());
			db.updateTransformation();
			
		}
				
		scale = DexUtils.hadimard(scale, v);
		for (DexterityDisplay sub : subdisplays) sub.scale(v);
	}
	
	@Deprecated
	public void resetScale(Vector v) {
		scale = v.clone();
	}
	
	/**
	 * Teleport the selection so that its corner aligns with the nearest whole block
	 */
	public void align() {
		align(false);
	}
	
	/**
	 * Teleport the selection so that its corner or center aligns with the nearest whole block
	 * @param toCenter If true, aligns based on the display's center, otherwise by its corner.
	 */
	public void align(boolean toCenter) {
		align(toCenter, true, true, true);
	}
	
	/**
	 * Teleport the selection so that its corner or center aligns with the nearest whole block along a certain axis of movement
	 * @param toCenter If true, aligns based on the display's center, otherwise by its corner.
	 * @param x If x axis movement is allowed
	 * @param y If y axis movement is allowed
	 * @param z If z axis movement is allowed
	 */
	public void align(boolean toCenter, boolean x, boolean y, boolean z) {
		if (!(x || y || z)) throw new IllegalArgumentException("Must align in at least one axis");
		Vector diff;
		if (toCenter) {
			Vector locv = center.toVector();
			Vector locvb = new Vector(Math.round(locv.getX()), Math.round(locv.getY()), Math.round(locv.getZ()));
			diff = locvb.clone().subtract(locv);
		} else {
			DexBlock block = null;
			double minx = 0, miny = 0, minz = 0;
			for (DexBlock b : blocks) {
				Location loc = b.getLocation().add(b.getTransformation().getDisplacement());
				if (block == null || (loc.getX() <= minx && loc.getY() <= miny && loc.getZ() <= minz)) {
					block = b;
					minx = loc.getX();
					miny = loc.getY();
					minz = loc.getZ();
				}
			}
			if (block == null) return;
			Location loc = block.getLocation().add(block.getTransformation().getDisplacement());
			diff = loc.clone().subtract(DexUtils.blockLoc(loc.clone())).toVector().multiply(-1);
		}
		
		if (!x) diff.setX(0);
		if (!y) diff.setY(0);
		if (!z) diff.setZ(0);
		teleport(diff);
		
	}
	
	/**
	 * Rotate the display along the yaw, pitch, and roll directions
	 * @param yawDeg
	 * @param pitchDeg
	 * @param rollDeg
	 * @return The quaternion representing the rotation
	 */
	public Quaterniond rotate(float yawDeg, float pitchDeg, float rollDeg) {
		RotationPlan plan = new RotationPlan();
		plan.yawDeg = yawDeg;
		plan.pitchDeg = pitchDeg;
		plan.rollDeg = rollDeg;
		return rotate(plan);
	}
	
	/**
	 * Reset the rotation for yaw, pitch, and roll directions
	 * @param yawDeg
	 * @param pitchDeg
	 * @param rollDeg
	 * @return The quaternion representing the rotation
	 */
	public Quaterniond setRotation(float yawDeg, float pitchDeg, float rollDeg) {
		RotationPlan plan = new RotationPlan();
		plan.yawDeg = yawDeg;
		plan.pitchDeg = pitchDeg;
		plan.rollDeg = rollDeg;
		plan.setYaw = true;
		plan.setPitch = true;
		plan.setRoll = true;
		return rotate(plan);
	}
	
	/**
	 * Reset the direction axes to the specified yaw, pitch, and roll in degrees
	 * @param yaw
	 * @param pitch
	 * @param roll
	 */
	public void setBaseRotation(float yaw, float pitch, float roll) {
		if (rot == null) rot = new DexRotation(this, yaw, pitch, roll);
		else rot.setAxes(yaw, pitch, roll);
	}
	
	/**
	 * Reset the direction axes to the specified x, y, and z orthonormal vectors
	 * @param x
	 * @param y
	 * @param z
	 */
	public void setBaseRotation(Vector x, Vector y, Vector z) {
		rot = new DexRotation(this, x, y, z);
	}
	
	/**
	 * Gets the rotation manager if one is already created
	 * @return
	 */
	public DexRotation getRotationManager() {
		return rot;
	}
	
	/**
	 * Gets or creates a rotation manager
	 * @param createNew
	 * @return the existing or new rotation manager
	 */
	public DexRotation getRotationManager(boolean createNew) {
		if (rot == null && createNew) rot = new DexRotation(this);
		return rot;
	}
	
	/**
	 * Rotate the selection
	 * @param plan
	 * @return The quaternion representing the rotation
	 */
	public Quaterniond rotate(RotationPlan plan) {
		if (rot == null) rot = new DexRotation(this);
		return rot.rotate(plan);
	}
	
	public BoundingBox getBoundingBox() {
		if (blocks.size() == 0) return new BoundingBox();
		double minx = Double.MAX_VALUE, miny = Double.MAX_VALUE, minz = Double.MAX_VALUE;
		double maxx = Double.MIN_VALUE, maxy = Double.MIN_VALUE, maxz = Double.MIN_VALUE;
		
		for (DexBlock db : blocks) {
			Vector v = db.getLocation().toVector(), scale = db.getTransformation().getScale().clone().multiply(0.5);
			Vector a = v.clone().add(scale), b = v.clone().subtract(scale);
			
			minx = Math.min(a.getX(), Math.min(b.getX(), minx));
			miny = Math.min(a.getY(), Math.min(b.getY(), miny));
			minz = Math.min(a.getZ(), Math.min(b.getZ(), minz));
			
			maxx = Math.max(a.getX(), Math.max(b.getX(), maxx));
			maxy = Math.max(a.getY(), Math.max(b.getY(), maxy));
			maxz = Math.max(a.getZ(), Math.max(b.getZ(), maxz));
		}
		
		return new BoundingBox(minx, miny, minz, maxx, maxy, maxz);
	}
	
	/**
	 * Re-sorts the blocks list in the standard y, x, z order
	 */
	public void sortBlocks() {
		double epsilon = 0.00001;
		blocks.sort((l, r) -> {
			Location l1 = l.getEntity().getLocation(), l2 = r.getEntity().getLocation();
			
			if (Math.abs(l1.getY() - l2.getY()) < epsilon) {
				if (Math.abs(l1.getX() - l2.getX()) < epsilon) {
					if (Math.abs(l1.getZ() - l2.getZ()) < epsilon) return 0;
					return l1.getZ() > l2.getZ() ? 1 : -1;
				}
				return l1.getX() > l2.getX() ? 1 : -1;
			}
			return l1.getY() > l2.getY() ? 1 : -1;
		});
	}
	
	/**
	 * Consolidate along all axes to reduce the number of entities where possible without altering the selection's shape
	 * @param m Mask to use, or null for no mask
	 */
	public void consolidate(Mask m) {
		consolidate(m, null);
	}
	
	/**
	 * Consolidate along all axes to reduce the number of entities where possible without altering the selection's shape
	 * @param m Mask to use, or null for no mask
	 * @param t The transaction to set blocks to or null
	 */
	public void consolidate(Mask m, BlockTransaction t) {

		new BukkitRunnable() {
			@Override
			public void run() {
				HashMap<OrientationKey, List<DexBlock>> grouped = new HashMap<>();
				HashMap<OrientationKey, Quaternionf> qmap = new HashMap<>();
				HashMap<DexBlock, Vector> deltas = new HashMap<>();
				List<DexBlock> toRemove = new ArrayList<>();
				Vector centerv = center.toVector();
				
				//group blocks by rotation orientation
				for (DexBlock db : blocks) {
					if (m != null && !m.isAllowed(db.getEntity().getBlock().getMaterial())) continue;
					OrientationKey key = new OrientationKey(db.getEntity().getLocation().getYaw(), db.getEntity().getLocation().getPitch(), db.getTransformation().getLeftRotation());
					List<DexBlock> group = grouped.get(key);
					Quaternionf q = qmap.get(key);
					if (group == null) {
						group = new ArrayList<DexBlock>();
						grouped.put(key, group);

						q = DexUtils.cloneQ(key.getQuaternion());
						q.w = -q.w;
						q.rotateX((float) Math.toRadians(-key.getPitch()));
						q.rotateY((float) Math.toRadians(key.getYaw()));

						qmap.put(key, q);
					}

					group.add(db);

					Vector3d diff = DexUtils.vectord(db.getLocation().toVector().subtract(centerv));
					q.transform(diff);
					db.setTempVector(DexUtils.vector(diff));
				}

				//perform consolidation for each group
				HashMap<Material, Vector> sizeMap = new HashMap<>();
				for (Entry<OrientationKey, List<DexBlock>> entry : grouped.entrySet()) {
					Quaternionf q = qmap.get(entry.getKey());
					consolidate(entry.getValue(), 0, q, toRemove, deltas, sizeMap);
					consolidate(entry.getValue(), 1, q, toRemove, deltas, sizeMap);
					consolidate(entry.getValue(), 2, q, toRemove, deltas, sizeMap);
				}

				HashMap<OrientationKey, RollOffset> roMap = new HashMap<>();
				for (DexBlock db : blocks) {
					db.setTempVector(null);
					if (db.getRoll() != 0) { //update roll
						OrientationKey key = new OrientationKey(db.getEntity().getLocation().getYaw(), db.getEntity().getLocation().getPitch(), db.getTransformation().getLeftRotation());
						RollOffset ro = roMap.get(key);
						if (ro == null) {
							ro = new RollOffset(db.getTransformation().getLeftRotation(), db.getTransformation().getScale());
							roMap.put(key, ro);
						}
						db.getTransformation().setRollOffset(ro.getOffset());
					}
				}
				
				new BukkitRunnable() { //sync thread to send packets
					@Override
					public void run() {
						for (DexBlock db : toRemove) db.remove();
						for (Entry<DexBlock, Vector> entry : deltas.entrySet()) {
							entry.getKey().move(entry.getValue());
							entry.getKey().updateTransformation();
						}
						if (t != null) t.commit(getBlocks(), m, true);
					}
				}.runTask(plugin);
				
			}
		}.runTaskAsynchronously(plugin);
	}
	
	@Deprecated
	public void consolidate(int axis, Mask m) {
		Bukkit.getLogger().severe("Use DexterityDisplay#consolidate() instead of consolidate(int axis, Mask m), deprecated as of v1.1.3");
		consolidate(m);
	}

	/**
	 * Consolidate along a specific axis
	 * @param axis 0 for X, 1 for Y, 2 for Z
	 * @param m Mask to use, or null for no mask
	 */
	private void consolidate(List<DexBlock> rotBlocks, int axis, Quaternionf q, List<DexBlock> toRemove, HashMap<DexBlock, Vector> deltas, HashMap<Material, Vector> sizeMap) { //assumed all same rotation
		if (rotBlocks.size() <= 1) return;
		double epsilon = 0.001;
		rotBlocks.sort((l, r) -> {
			double a = DexUtils.getParameter(l.getTempVector(), axis), b = DexUtils.getParameter(r.getTempVector(), axis);
			if (Math.abs(a - b) < epsilon) return 0;
			return a > b ? 1 : -1;
		});
		
		for (int i = 0; i < rotBlocks.size(); i++) {
			DexBlock prev = rotBlocks.get(i);
			Vector prev_loc = prev.getTempVector();
			Vector s1 = prev.getTransformation().getScale();
			double s1min = DexUtils.minValue(s1);
			
			for (int j = i+1; j < rotBlocks.size(); j++) { //find the first block that is in the same axis, optimized by sort
				DexBlock db = rotBlocks.get(j);
				Vector loc = db.getTempVector();
				Vector s2 = db.getTransformation().getScale();
				double epsilon2 = 0.001*Math.min(s1min, DexUtils.minValue(s2));
				
				//if differs only by current axis
				if (Math.abs(DexUtils.getParameter(loc, axis+1) - DexUtils.getParameter(prev_loc, axis+1)) < epsilon2
						&& Math.abs(DexUtils.getParameter(loc, axis+2) - DexUtils.getParameter(prev_loc, axis+2)) < epsilon2) {

					//if same type
					if (db.getEntity().getBlock().equals(prev.getEntity().getBlock())
							&& Math.abs(DexUtils.getParameter(s2, axis+1) - DexUtils.getParameter(s1, axis+1)) < epsilon2
							&& Math.abs(DexUtils.getParameter(s2, axis+2) - DexUtils.getParameter(s1, axis+2)) < epsilon2) {

						Vector blocksize = sizeMap.get(prev.getEntity().getBlock().getMaterial());
						if (blocksize == null) {
							blocksize = DexUtils.getBlockDimensions(prev.getEntity().getBlock());
							sizeMap.put(prev.getEntity().getBlock().getMaterial(), blocksize);
						}
						
						double diff = DexUtils.getParameter(loc, axis) - DexUtils.getParameter(prev_loc, axis),
								threshold = DexUtils.getParameter(blocksize, axis)*(DexUtils.getParameter(s2, axis) + DexUtils.getParameter(s1, axis))/2;
						
						//if close enough to be touching/overlapping, perform the consolidation between pair
						if (diff - threshold <= epsilon2) {
							double newLen = (threshold + diff) / DexUtils.getParameter(blocksize, axis);
							double disp = DexUtils.getParameter(prev.getTransformation().getDisplacement(), axis);
							
							DexUtils.setParameter(s1, axis, newLen);
							DexUtils.setParameter(prev.getTransformation().getDisplacement(), axis, -newLen/2);
							
							Vector del = DexUtils.oneHot(axis, disp + (newLen*0.5));
							del = DexUtils.vector(q.transformInverse(DexUtils.vectord(del)));
							Vector existingDel = deltas.get(prev);
							if (existingDel != null) del.add(existingDel);
							deltas.put(prev, del);
							toRemove.add(db);
							rotBlocks.remove(db);
						}
					}
					break;
				}
			}
		}
	}

}
