package me.c7dev.dexterity.util;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Vector;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import me.c7dev.dexterity.displays.DexterityDisplay;

/**
 * Provides a wrapper for a {@link BlockDisplay} entity that holds data about its {@link DexTransformation}, roll offset, and more
 */
public class DexBlock {

	private UUID uuid;
	private BlockDisplay entity;
	private DexTransformation trans;
	private DexterityDisplay disp;
	private float roll = 0;
	private Vector tempv;
	
	public static final int TELEPORT_DURATION = 2;
	
	/**
	 * Convert a block into block display
	 * @param block The block to convert
	 * @param d The selection to register the new block display under
	 */
	public DexBlock(Block block, DexterityDisplay d) {
		disp = d;
		trans = DexTransformation.newDefaultTransformation();
		entity = d.getPlugin().spawn(block.getLocation().clone().add(0.5, 0.5, 0.5), BlockDisplay.class, spawned -> {
			spawned.setBlock(block.getBlockData());
			spawned.setTransformation(trans.build());
			spawned.setInterpolationDuration(TELEPORT_DURATION);
		});
		uuid = entity.getUniqueId();
		d.getPlugin().setMappedDisplay(this);
		block.setType(Material.AIR);
	}
	
	/**
	 * Create a wrapper for a block display that is part of a selection
	 * @param bd The block display to register
	 * @param d The selection to register the new block display under
	 */
	public DexBlock(BlockDisplay bd, DexterityDisplay d) {
		this(bd, d, 0f);
	}
	
	/**
	 * Create a wrapper for a block display that is part of a selection.
	 * Must manually subtract the {@link RollOffset} afterwards
	 * 
	 * @param bd The block display to register
	 * @param d The selection to register the new block display under
	 * @param roll The roll, in degrees, that will forced into the wrapper
	 */
	@Deprecated //must manually subtract roll offset, used in placing db for efficiency
	public DexBlock(BlockDisplay bd, DexterityDisplay d, float roll) {
		entity = bd;
		disp = d;
		uuid = bd.getUniqueId();
		this.roll = roll;
		bd.setInterpolationDuration(TELEPORT_DURATION);
		trans = new DexTransformation(bd.getTransformation());
		d.getPlugin().setMappedDisplay(this);
	}
	
	/**
	 * Spawn a block display wrapper based on a previously recorded state
	 * 
	 * @param state
	 */
	public DexBlock(DexBlockState state) {
		this(state, null);
	}
	
	/**
	 * Spawn a block display wrapper based on a previously recorded state
	 * 
	 * @param state
	 * @param offset Translation to put block at a different location
	 */
	public DexBlock(DexBlockState state, Vector offset) {
		disp = state.getDisplay();
		trans = state.getTransformation();
		Location loc = state.getLocation();
		if (offset != null) loc = loc.clone().add(offset);
		entity = state.getDisplay().getPlugin().spawn(loc, BlockDisplay.class, a -> {
			a.setBlock(state.getBlock());
			a.setTransformation(state.getTransformation().build());
			if (state.getGlow() != null) {
				a.setGlowColorOverride(state.getGlow());
				a.setGlowing(true);
			}
		});
		uuid = state.getUniqueId();
		if (uuid == null) uuid = entity.getUniqueId();
		
		roll = state.getRoll();
		if (state.getDisplay() != null) {
			state.getDisplay().getPlugin().setMappedDisplay(this);
			state.getDisplay().addBlock(this);
		}
	}
	
	public UUID getUniqueId() {
		return uuid;
	}
	
	/**
	 * Calculate the roll, recommended to do async (done automatically when plugin is enabled)
	 * @param cache Modifyable cache for similar rotation orientations
	 */
	public void loadRoll(HashMap<OrientationKey, RollOffset> cache) {
		Quaternionf r = trans.getLeftRotation();
		
		OrientationKey key = new OrientationKey(trans.getScale().getX(), trans.getScale().getY(), r);
		RollOffset cached = cache.get(key);
		
		
		
		if (cached != null) {
			trans.getDisplacement().add(trans.getRollOffset());
			trans.setRollOffset(cached.getOffset());
			trans.getDisplacement().subtract(cached.getOffset());
			roll = cached.getRoll();
		} else {
			if (r.w != 0) {
				if (r.x == 0 && r.y == 0 && r.z != 0) {
					RollOffset c = new RollOffset(r, trans.getScale());
					trans.getDisplacement().add(trans.getRollOffset());
					trans.setRollOffset(c.getOffset());
					trans.getDisplacement().subtract(c.getOffset());
					roll = c.getRoll();
					cache.put(key, c);
				}
			}
		}
	}
	
	/**
	 * Calculate the roll, recommended to do async (done automatically when plugin is enabled)
	 */
	public void loadRoll() { //async
		Quaternionf r = trans.getLeftRotation();
		
		if (r.w != 0) {
			if (r.x == 0 && r.y == 0 && r.z != 0) {
				RollOffset c = new RollOffset(r, trans.getScale());
				trans.getDisplacement().add(trans.getRollOffset());
				trans.setRollOffset(c.getOffset());
				trans.getDisplacement().subtract(c.getOffset());
				roll = c.getRoll();
			}
		}
	}
	
	/**
	 * Sets up the Block Display's transformation to follow Dexterity convention, centers the block display, and loads roll data
	 */
	public void loadTransformationAndRoll() {
		double lw = entity.getTransformation().getLeftRotation().w, rw = entity.getTransformation().getRightRotation().w;
		if (lw != 1 && rw != 1) return;
		Quaternionf q = entity.getTransformation().getLeftRotation();
		q.mul(entity.getTransformation().getRightRotation());
		
		Quaterniond rot = new Quaterniond();
		rot.rotateY(-Math.toRadians(entity.getLocation().getYaw()));
		rot.rotateX(Math.toRadians(entity.getLocation().getPitch()));
		
		Location visibleCenter = entity.getLocation()
				.add(DexUtils.vector(rot.transform(entity.getTransformation().getTranslation())))
				.add(DexUtils.vector(rot.transform(q.transform(entity.getTransformation().getScale().mul(0.5f)))));
		AxisPair ap = new AxisPair();
		
		rot.mul(DexUtils.quaternion(q));
		ap.transform(rot);
		Vector pyr = ap.getPitchYawRoll();
		
		Vector scale = DexUtils.vector(entity.getTransformation().getScale());
		RollOffset ro = new RollOffset((float) pyr.getZ(), scale);
		
		DexTransformation trans = new DexTransformation();
		trans.setDisplacement(scale.clone().multiply(-0.5));
		trans.setScale(scale);
		trans.setLeftRotation(ro.getQuaternion());
		trans.setRollOffset(ro.getOffset());
		
		roll = ro.getRoll();
		this.trans = trans;
		entity.teleport(visibleCenter);
		entity.setTransformation(trans.build());
		entity.setRotation((float) pyr.getY(), (float) pyr.getX());
		entity.setInterpolationDelay(TELEPORT_DURATION);
	}
	
	public BlockDisplay getEntity() {
		return this.entity;
	}
	
	/**
	 * Get the temporary vector, used in processing
	 */
	public Vector getTempVector() {
		return tempv;
	}
	
	/**
	 * Set the temporary vector, used in processing
	 */
	public void setTempVector(Vector v) {
		if (tempv != null && v != null) throw new IllegalArgumentException("Illegal concurrent modification to block!");
		tempv = v;
	}
	
	@Deprecated
	public void setDexterityDisplay(DexterityDisplay d) {
		if (d == null) return;
		disp = d;
	}
	
	/**
	 * Gets the roll in degrees. Yaw and pitch can be retrieved from the entity's location
	 * @return The roll in degrees
	 */
	public float getRoll() {
		return roll;
	}
	
	/**
	 * Set the glow of the block entity
	 * @param glow
	 */
	public void setGlow(Color color) {
		if (color == null) entity.setGlowing(false);
		else {
			entity.setGlowColorOverride(color);
			entity.setGlowing(true);
		}
	}
	
	/**
	 * Set a new transformation for the given roll
	 * @param f The roll in degrees
	 */
	public void setRoll(float f) { //TODO potential optimization is to store same vec, quaternion ref for many db
		if (Math.abs(f - roll) < 0.0000001) return;
		RollOffset c = new RollOffset(f, trans.getScale());
		trans.setLeftRotation(c.getQuaternion());
		trans.setRollOffset(c.getOffset());
		roll = f;
		updateTransformation();
	}
	
	public DexterityDisplay getDexterityDisplay() {
		return disp;
	}
		
	public DexTransformation getTransformation() {
		return trans;
	}
	
	public DexBlockState getState() {
		return new DexBlockState(this);
	}
	
	/**
	 * Loads in another state without spawning a new entity
	 * @param state
	 */
	public void loadState(DexBlockState state) {
		if (entity.isDead()) return;
		trans = state.getTransformation();
		roll = state.getRoll();
		entity.teleport(state.getLocation());
		entity.setTransformation(state.getTransformation().build());
		entity.setBlock(state.getBlock());
	}
		
	/**
	 * Sets the transformation wrapper and updates the entity
	 * @param dt
	 */
	public void setTransformation(DexTransformation dt) {
		trans = dt;
		entity.setTransformation(dt.build());
	}
	
	/**
	 * Updates the entity's transformation to the current values of its mutable {@link DexTransformation}
	 */
	public void updateTransformation() {
		
		entity.setTransformation(trans.build());
	}
	
	public void teleport(Location loc) {
		entity.teleport(loc);
	}
	
	/**
	 * Move the block display entity by an offset
	 * @param v
	 */
	public void move(Vector v) {
		entity.teleport(entity.getLocation().add(v));
	}
	
	/**
	 * Move the block display entity by an offset
	 * @param x Distance in blocks
	 * @param y Distance in blocks
	 * @param z Distance in blocks
	 */
	public void move(double x, double y, double z) {
		entity.teleport(entity.getLocation().add(x, y, z));
	}
	
//	public void setBrightness(int blockLight, int skyLight) {
//		entity.setBrightness(new Brightness(blockLight, skyLight));
//	}
	
	/**
	 * Kill entity and unregister from selection
	 */
	public void remove() {
		disp.removeBlock(this);
		disp.getPlugin().clearMappedDisplay(this);
		entity.remove();
		if (disp.getBlocks().length == 0 && disp.getSubdisplayCount() == 0) {
			disp.remove(false);
		}
	}
	
	public int hashCode() {
		return uuid.hashCode();
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DexBlock)) return false;
		DexBlock db = (DexBlock) o;
		return entity.getUniqueId().equals(db.getEntity().getUniqueId());
	}
	
	/**
	 * Roll-offset adjusted location of the center of the block display entity.
	 * This is not necessarily the entity's location if other plugins are being used.
	 * 
	 * @return Unmodifiable location of the center of the block display
	 */
	public Location getLocation() {
		return entity.getLocation().add(trans.getDisplacement()).add(trans.getScale().clone().multiply(0.5));
	}

}
