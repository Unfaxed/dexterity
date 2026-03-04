package me.c7dev.dexterity.interaction;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import me.c7dev.dexterity.api.DexterityAPI;
import me.c7dev.dexterity.util.ClickedBlockDisplay;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.OrientationKey;
import me.c7dev.dexterity.util.RollOffset;

public class DexClickDetector {
	
	private static final double MAGNITUDE_MULTIPLIER = -0.375, DOT_PRODUCT_THRESHOLD = 0.75;
	
	private static final BlockFace[] FACES = {
			BlockFace.UP,
			BlockFace.DOWN,
			BlockFace.SOUTH,
			BlockFace.NORTH,
			BlockFace.EAST,
			BlockFace.WEST
	};
	
	private final DexterityAPI api;
	private final ClickDataCache cache;
	
	private final Vector[][] basisVecsNoRot = getBasisVecs(new Matrix3d(1, 0, 0, 0, 1, 0, 0, 0, 1));
	private final Vector eastUnit = new Vector(1, 0, 0), upUnit = new Vector(0, 1, 0), southUnit = new Vector(0, 0, 1);
	private Vector scaleRaw, scale, eyeLoc, dir;
	
	private Vector upDir, southDir, eastDir;
	private Vector[][] basisVecs;
	private Location loc;
	private RollOffset ro;
	
	private double radius = 4;
	
	public DexClickDetector(DexterityAPI api, ClickDataCache cache) {
		this.api = api;
		this.cache = cache;
		if (cache == null) throw new RuntimeException("Click data cache is null");
	}
	
	public void setEntityDetectionRadius(double radius) {
		this.radius = radius;
	}
	
	/**
	 * Get the data about the block display that an online player is currently looking at
	 * @param p
	 * @return
	 */
	public ClickedBlockDisplay getLookingAt(Player p) {
		List<Entity> near = p.getNearbyEntities(radius, radius, radius);
		return getLookingAt(p.getLocation().getDirection(), p.getEyeLocation().toVector(), near);
	}
	
	/**
	 * Get the data about a block display that a direction vector points to given the camera's location vector and nearby entities
	 * @param near
	 * @param dir Unit direction vector
	 * @param eyeLoc
	 * @return
	 */
	public ClickedBlockDisplay getLookingAt(Vector dir, Location eyeLoc, List<Entity> nearbyEntities) {
		return getLookingAt(dir.clone().normalize(), eyeLoc.toVector(), nearbyEntities);
	}
	
	/**
	 * Get the data about a block display that a direction vector points to given the camera's location vector and nearby entities
	 * @param dir
	 * @param eyeLoc
	 * @param nearbyEntities
	 * @return
	 */
	private ClickedBlockDisplay getLookingAt(Vector dir, Vector eyeLoc, List<Entity> nearbyEntities) {
		this.dir = dir;
		this.eyeLoc = eyeLoc;
		
		double minDist = Double.MAX_VALUE;
		ClickedBlockDisplay nearest = null;
				
		bdLoop: for (Entity entity : nearbyEntities) {
			if (!(entity instanceof BlockDisplay) || api.isMarkerPoint(entity)) continue;
			BlockDisplay e = (BlockDisplay) entity;
			scaleRaw = DexUtils.vector(e.getTransformation().getScale());
			if (scaleRaw.getX() < 0 || scaleRaw.getY() < 0 || scaleRaw.getZ() < 0) continue; //TODO figure out displacement to center
			scaleRaw.multiply(0.5);
			scale = DexUtils.hadimard(DexUtils.getBlockDimensions(e.getBlock()), scaleRaw);
			
			if (!dotProductCheck(e)) continue;
			
			loadBasisVectors(e);
			loadBlockDisplayLocation(e);

			Vector[] locs = generateFaceVectors(loc.toVector(), scale);
						
			boolean firstFace = false; //always intersects 2 faces - convex
			for (int i = 0; i < locs.length; i++) {
				if (!firstFace && i == locs.length - 1) continue bdLoop; //not looking at this block, no need to check last face
				
				Vector basis1 = basisVecs[i][0], basis2 = basisVecs[i][1];
								
				Vector soln = solveMatrix(locs[i], basis1, basis2);
				double dist = -soln.getZ(); //distance from player's eye to precise location on surface in units of blocks (magic :3)
				
				if (dist < 0 || !isInBlockFace(i, soln)) continue; //behind head or not in the block face
				
				if (dist < minDist) {
					Vector rawOffset = basis1.clone().multiply(soln.getX())
							.add(basis2.clone().multiply(soln.getY()));
					Vector blockOffset = locs[i].clone().add(rawOffset); //surface location
					
					minDist = dist;
					nearest = new ClickedBlockDisplay(e, FACES[i], rawOffset, DexUtils.location(loc.getWorld(), blockOffset), 
							loc, upDir, eastDir, southDir, dist);
					if (ro != null) nearest.setRollOffset(ro);
				}
				
				if (firstFace) continue bdLoop;
				else firstFace = true;
			}	
		}
		
		return nearest;
	}
	
	// Solve `(FaceCenter) + a(basis1) + b(basis2) = c(dir) + (EyeLoc)` to find intersection of block face plane
	private Vector solveMatrix(Vector faceCenter, Vector basis1, Vector basis2) {
		Vector L = eyeLoc.clone().subtract(faceCenter);
		Matrix3f matrix = new Matrix3f(
				(float) basis1.getX(), (float) basis1.getY(), (float) basis1.getZ(),
				(float) basis2.getX(), (float) basis2.getY(), (float) basis2.getZ(),
				(float) dir.getX(), (float) dir.getY(), (float) dir.getZ()
				);
		matrix.invert();
		Vector3f cf = new Vector3f();
		return DexUtils.vector(matrix.transform(DexUtils.vector(L), cf));
	}
	
	private void loadBlockDisplayLocation(BlockDisplay e) {
		DexBlock db = api.getPlugin().getMappedDisplay(e.getUniqueId());
		
		//calculate roll and its offset
		if (db == null) {
			Vector displacement = DexUtils.vector(e.getTransformation().getTranslation());
			if (e.getTransformation().getLeftRotation().w != 0) {
				OrientationKey key = new OrientationKey(e.getTransformation().getScale().x,
						e.getTransformation().getScale().y,
						e.getTransformation().getLeftRotation());
				ro = cache.getCachedRollOffset(key); //does not account for pitch and yaw built into the rotation quaternion, assumed that blocks managed by other plugins are not built on
				if (ro == null) {
					ro = new RollOffset(e.getTransformation().getLeftRotation(), DexUtils.vector(e.getTransformation().getScale()));
					cache.setCachedRollOffset(key, ro);
				}
				displacement.subtract(ro.getOffset());
			}
			loc = e.getLocation().add(displacement).add(scaleRaw);
		} else {
			loc = db.getLocation(); //already handled by DexTransformation
		}
		
		//calculate location of visual display accounting for axis asymmetry
		loc.add(upDir.clone().multiply(scale.getY() - scaleRaw.getY()));
	}
	
	private boolean dotProductCheck(BlockDisplay e) {
		//check if the player is looking in the general direction of the block, accounting for scale
		Vector diff = e.getLocation().toVector().subtract(eyeLoc).normalize();
		double dot = diff.dot(dir);
		return dot >= (MAGNITUDE_MULTIPLIER * scale.lengthSquared()) + DOT_PRODUCT_THRESHOLD; //TODO: taylor series to improve the approximation
	}
	
	private boolean isInBlockFace(int blockFaceNum, Vector soln) {
		switch(blockFaceNum) { //check within block face
		case 0:
		case 1:
			if (Math.abs(soln.getX()) > scale.getX()) return false;
			if (Math.abs(soln.getY()) > scale.getZ()) return false;
			break;
		case 2:
		case 3:
			if (Math.abs(soln.getX()) > scale.getX()) return false;
			if (Math.abs(soln.getY()) > scale.getY()) return false;
			break;
		default:
			if (Math.abs(soln.getX()) > scale.getZ()) return false;
			if (Math.abs(soln.getY()) > scale.getY()) return false;
		}
		return true;
	}
	
	//if rotated, we need to transform the displacement vecs and basis vectors accordingly
	private void loadBasisVectors(BlockDisplay e) {
		if (e.getLocation().getYaw() != 0 || e.getLocation().getPitch() != 0 || e.getTransformation().getLeftRotation().w != 1) {
			
			OrientationKey key = new OrientationKey(e.getLocation().getYaw(), e.getLocation().getPitch(), e.getTransformation().getLeftRotation());
			Vector[] res = cache.getCachedAxis(key);
			if (res == null) {
				Vector3f eastDir3f = new Vector3f(1, 0, 0), upDir3f = new Vector3f(0, 1, 0), southDir3f = new Vector3f(0, 0, 1);
				Quaternionf q = DexUtils.cloneQ(e.getTransformation().getLeftRotation());
				q.z = -q.z;
				q.rotateX((float) -Math.toRadians(e.getLocation().getPitch()));
				q.rotateY((float) Math.toRadians(e.getLocation().getYaw()));

				q.transformInverse(eastDir3f);
				q.transformInverse(upDir3f);
				q.transformInverse(southDir3f);

				eastDir = DexUtils.vector(eastDir3f);
				upDir = DexUtils.vector(upDir3f);
				southDir = DexUtils.vector(southDir3f);
				basisVecs = getBasisVecs(eastDir, upDir, southDir);
				
				Vector[] res2 = {eastDir, upDir, southDir};
				cache.cacheAxis(key, res2);
				
			} else {
				eastDir = res[0];
				upDir = res[1];
				southDir = res[2];
				basisVecs = getBasisVecs(eastDir, upDir, southDir);
			}
		} else {
			eastDir = eastUnit;
			upDir = upUnit;
			southDir = southUnit;
			basisVecs = basisVecsNoRot;
		}
	}
	
	private Vector[] generateFaceVectors(Vector locv, Vector scale) {
		//block face centers
		Vector up = locv.clone().add(upDir.clone().multiply(scale.getY())), 
				down = locv.clone().add(upDir.clone().multiply(-scale.getY())),
				south = locv.clone().add(southDir.clone().multiply(scale.getZ())), 
				north = locv.clone().add(southDir.clone().multiply(-scale.getZ())),
				east = locv.clone().add(eastDir.clone().multiply(scale.getX())), 
				west = locv.clone().add(eastDir.clone().multiply(-scale.getX()));

		Vector[] locs = {up, down, south, north, east, west};
		return locs;
	}
	
	private Vector[][] getBasisVecs(Matrix3d mat) {
		Vector a = new Vector(mat.m00, mat.m01, mat.m02).normalize(), 
				b = new Vector(mat.m10, mat.m11, mat.m12).normalize(), 
				c = new Vector(mat.m20, mat.m21, mat.m22).normalize();
		Vector[][] basisVecs = {{a, c}, {a, c}, {a, b}, {a.clone().multiply(-1), b}, {c.clone().multiply(-1), b}, {c, b}};
		return basisVecs;
	}
	
	private Vector[][] getBasisVecs(Vector a, Vector b, Vector c){
		Vector[][] basisVecs = {{a, c}, {a, c}, {a, b}, {a.clone().multiply(-1), b}, {c.clone().multiply(-1), b}, {c, b}};
		return basisVecs;
	}

}
