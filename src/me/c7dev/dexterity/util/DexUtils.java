package me.c7dev.dexterity.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Snow;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.sk89q.worldedit.math.BlockVector3;

import me.c7dev.dexterity.Dexterity;

/**
 * Defines commonly used static methods used globally in the plugin or API
 */
public class DexUtils {
	
	public static ItemStack createItem(Material material, int amount, String name, String... lore) {
		ItemStack item = new ItemStack(material, amount);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		List<String> lore2 = new ArrayList<String>();
		for (String s : lore) lore2.add(s);
		meta.setLore(lore2);
		item.setItemMeta(meta);
		return item;
	}
	
	public static double max(Vector v) {
		double m = v.getX();
		if (v.getY() > m) m = v.getY();
		if (v.getZ() > m) m = v.getZ();
		return m;
	}
	
	public static double min(Vector v) {
		double m = v.getX();
		if (v.getY() < m) m = v.getY();
		if (v.getZ() < m) m = v.getZ();
		return m;
	}
	
	public static String round(double d, int decimals) {
		StringBuilder hashs = new StringBuilder();
		for (int i = 0; i < decimals; i++) hashs.append('#');
		DecimalFormat df = new DecimalFormat("#." + hashs.toString());
		return df.format(d);
	}
	
	public static String locationString(Location loc, int decimals) {
		return vectorString(loc.toVector(), decimals);
	}
	
	public static String vectorString(Vector loc, int decimals) {
		if (decimals == 0) {
			return "X:" + (int) loc.getX() + 
					" Y:" + (int) loc.getY() +
					" Z:" + (int) loc.getZ();
		}
		return "X:" + DexUtils.round(loc.getX(), decimals) + 
		" Y:" + DexUtils.round(loc.getY(), decimals) +
		" Z:" + DexUtils.round(loc.getZ(), decimals);
	}
	
	public static String quaternionString(Quaternionf q, int decimals) {
		return quaternionString(new Quaterniond(q), decimals);
	}
	
	public static String quaternionString(Quaterniond q, int decimals) {
		return DexUtils.round(q.x, decimals) + ", " + DexUtils.round(q.y, decimals) + ", " + DexUtils.round(q.z, decimals) + ", " + DexUtils.round(q.w, decimals);  
	}
	
	public static Location blockLoc(Location loc) {
		loc.setX(loc.getBlockX());
		loc.setY(loc.getBlockY());
		loc.setZ(loc.getBlockZ());
		loc.setYaw(0);
		loc.setPitch(0);
		return loc;
	}
	
	public static double minValue(Vector v) {
		return Math.min(v.getX(), Math.min(v.getY(), v.getZ()));
	}
	
	public static String attrAlias(String s) {
		switch(s.toLowerCase()) {
		case "r": return "radius";
		case "east": return "x";
		case "west": return "x";
		case "up": return "y";
		case "down": return "y";
		case "north": return "z";
		case "south": return "z";
		default: return s.toLowerCase();
		}
	}
	
	public static int valueAlias(String s) {
		switch(s.toLowerCase()) {
		case "true": return 1;
		case "yes": return 1;
		case "y": return 1;
		case "t": return 1;
		case "false": return 0;
		case "no": return 0;
		case "n": return 0;
		case "f": return 0;
		case "down": return -1;
		case "west": return -1;
		case "north": return -1;
		default: 
			try {
				return Integer.parseInt(s);
			} catch(Exception ex){
				return 1;
			}
		}
	}
	
	public static List<String> getDefaultAttributesWithFlags(String[] args) { //could have negative numbers
		List<String> r = new ArrayList<>();
		for (int i = 1; i < args.length; i++) {
			String arg = args[i];
			if (!arg.contains("=") && !arg.contains(":")) {
				r.add(arg.toLowerCase());
			}
		}
		return r;
	}
	
	public static double faceToDirection(BlockFace face, Vector scale) {
		switch(face) {
		case UP: return scale.getY();
		case DOWN: return -scale.getY();
		case EAST: return scale.getX();
		case WEST: return -scale.getX();
		case SOUTH: return scale.getZ();
		case NORTH: return -scale.getZ();
		default: return 1;
		}
	}
	public static double faceToDirectionAbs(BlockFace face, Vector scale) {
		switch(face) {
		case UP: 
		case DOWN: return scale.getY();
		case EAST: 
		case WEST: return scale.getX();
		case SOUTH: 
		case NORTH: return scale.getZ();
		default: return 1;
		}
	}
	
	public static int parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (Exception ex) {
			return -2;
		}
	}
	
	public static Vector3f vector(Vector v) {
		return new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ());
	}
	public static Vector3d vectord(Vector v) {
		return new Vector3d(v.getX(), v.getY(), v.getZ());
	}
	public static Vector vector(Vector3f v) {
		return new Vector(v.x, v.y, v.z);
	}
	public static Vector vector(Vector3d v) {
		return new Vector(v.x, v.y, v.z);
	}
	public static Vector hadimard(Vector a, Vector b) {
		return new Vector(a.getX()*b.getX(), a.getY()*b.getY(), a.getZ()*b.getZ());
	}
	
	/**
	 * Returns the sum of x, y, and z, each scaled respectively by coefficients defined in u
	 * @param x1
	 * @param y1
	 * @param z1
	 * @param u
	 * @return
	 */
	public static Vector linearCombination(Vector x1, Vector y1, Vector z1, Vector u) {
		Vector v = new Vector();
		v.add(x1.clone().multiply(u.getX()));
		v.add(y1.clone().multiply(u.getY()));
		v.add(z1.clone().multiply(u.getZ()));
		return v;
	}
	public static Location location(World w, Vector v) {
		return new Location(w, v.getX(), v.getY(), v.getZ(), 0, 0);
	}
	public static Location location(World w, BlockVector3 v) {
		return new Location(w, v.getX(), v.getY(), v.getZ());
	}
	public static Quaternionf cloneQ(Quaternionf r) {
		return new Quaternionf(r.x, r.y, r.z, r.w);
	}
	public static Quaterniond cloneQ(Quaterniond r) {
		return new Quaterniond(r.x, r.y, r.z, r.w);
	}
	
	public static double getVolume(Location l1, Location l2) {
		double xmin = Math.min(l1.getX(), l2.getX()), xmax = Math.max(l1.getX(), l2.getX());
		double ymin = Math.min(l1.getY(), l2.getY()), ymax = Math.max(l1.getY(), l2.getY());
		double zmin = Math.min(l1.getZ(), l2.getZ()), zmax = Math.max(l1.getZ(), l2.getZ());		
		
		return Math.abs(xmax-xmin) * Math.abs(ymax-ymin) * Math.abs(zmax-zmin);
	}
	
	public static double getBlockVolume(Location l1, Location l2) {
		double xmin = Math.min(l1.getX(), l2.getX()), xmax = Math.max(l1.getX(), l2.getX()) + 1;
		double ymin = Math.min(l1.getY(), l2.getY()), ymax = Math.max(l1.getY(), l2.getY()) + 1;
		double zmin = Math.min(l1.getZ(), l2.getZ()), zmax = Math.max(l1.getZ(), l2.getZ()) + 1;	
		
		return Math.abs(xmax-xmin) * Math.abs(ymax-ymin) * Math.abs(zmax-zmin);
	}
	
	public static int maxPage(int size, int pagelen) {
		return (size/pagelen) + (size % pagelen > 0 ? 1 : 0);
	}
	
	public static void paginate(Player p, String[] strs, int page, int pagelen) {
		int maxpage = (strs.length/pagelen) + (strs.length % pagelen > 0 ? 1 : 0);
		if (page >= maxpage) page = maxpage - 1;
		int pagestart = pagelen*page;
		int pageend = Math.min(pagestart + pagelen, strs.length);
				
		for (int i = pagestart; i < pageend; i++) {
			if (strs[i] != null) p.sendMessage(strs[i]);
		}
	}
	
	public static Location deserializeLocation(FileConfiguration config, String dir) {
		String id = "%%__USER__%%, %%__RESOURCE__%%, %%__NONCE__%%";
		if (config.get(dir) == null) return null;
		
		double x = config.getDouble(dir + ".x");
		double y = config.getDouble(dir + ".y");
		double z = config.getDouble(dir + ".z");
		double yaw = config.getDouble(dir + ".yaw");
		double pitch = config.getDouble(dir + ".pitch");
		String world = config.getString(dir + ".world");
		return new Location(Bukkit.getWorld(world), x, y, z, (float) yaw, (float) pitch);
	}
	
	public static Vector getBlockDimensions(BlockData b) {
		Material mat = b.getMaterial();
		String m = mat.toString();
		if (m.endsWith("_SLAB")) return new Vector(1, 0.5, 1);
		if (m.endsWith("_TRAPDOOR")) return new Vector(1, 3.0/16, 1);
		if (m.endsWith("_FENCE")) return new Vector(0.25, 1, 0.25);
		if (m.endsWith("_BED")) return new Vector(1, 9.0/16, 1);
		if (m.endsWith("TORCH")) return new Vector(0.125, 0.625, 0.125);
		if (m.endsWith("_WALL")) return new Vector(0.5, 1, 0.5);
		if (m.endsWith("_CARPET") || m.endsWith("_PRESSURE_PLATE")) return new Vector(1, 1.0/16, 1);
		if (m.startsWith("POTTED")) {
			if (m.endsWith("_SAPLING")) return new Vector(0.625, 1, 0.625);
		}
		if (m.endsWith("CANDLE")) return new Vector(0.125, 7.0/16, 0.125);
		if (m.endsWith("RAIL")) return new Vector(1, 0.125, 1);
		
		//TODO doors, fences, gates
		BlockFace facing = null;
		if (b instanceof Directional) {
			Directional bd = (Directional) b;
			facing = bd.getFacing();
		}

		switch(mat) {
		case SNOW:
			Snow sd = (Snow) b;
			return new Vector(1, sd.getLayers() / 8.0, 1);
		case END_ROD:
		case LIGHTNING_ROD:
		case CHAIN: return new Vector(0.25, 1, 0.25);
		case IRON_BARS: return new Vector(0.125, 1, 0.125);
		case END_PORTAL_FRAME: return new Vector(1, 13.0/16, 1);
		case FLOWER_POT: return new Vector(0.375, 0.375, 0.357);
		case CAKE: return new Vector(0.875, 0.5, 0.875);
		case CHEST: return new Vector(0.875, 0.875, 0.875);
		case RED_MUSHROOM: return new Vector(0.5, 7.0/16, 0.5);
		case BROWN_MUSHROOM: return new Vector(0.5, 0.5, 0.5);
		case LILY_OF_THE_VALLEY:
		case CRIMSON_FUNGUS: return new Vector(0.625, 0.75, 0.625);
		case WARPED_FUNGUS: return new Vector(0.625, 9.0/16, 0.625);
		case DANDELION:
		case POTTED_BROWN_MUSHROOM:
		case POTTED_RED_MUSHROOM: return new Vector(0.375, 9.0/16, 0.375);
		case POTTED_CACTUS: return new Vector(0.375, 1, 0.375);
		case NETHER_PORTAL: return new Vector(1, 1, 0.25);
		case PINK_PETALS: return new Vector(1, 3.0/16, 1);
		case DAYLIGHT_DETECTOR: return new Vector(1, 0.375, 1);
		case RED_TULIP:
		case ORANGE_TULIP:
		case WHITE_TULIP:
		case PINK_TULIP: return new Vector(0.375, 13.0/16, 0.375);
		case POPPY:
		case WITHER_ROSE:
		case LANTERN:
		case SOUL_LANTERN: return new Vector(0.375, 11.0/16, 0.375);
		case BELL:
			if (facing == BlockFace.WEST || facing == BlockFace.EAST) return new Vector(0.25, 1, 1);
			else return new Vector(1, 1, 0.25);
		case REPEATER:
		case COMPARATOR: return new Vector(1, 7.0/16, 1);
		default: return new Vector(1, 1, 1);
		}
	}
	
	public static Vector3f cloneV(Vector3f x) {
		return new Vector3f(x.x, x.y, x.z);
	}
	
	public static Quaternionf quaternion(Quaterniond q) {
		return new Quaternionf(q.x, q.y, q.z, q.w);
	}
	
	public static Quaterniond quaternion(Quaternionf q) {
		return new Quaterniond(q);
	}
	
	public static List<String> materials(String start) {
		return materials(start, null);
	}
	
	public static List<String> materials(String start, String prefix) {
		List<String> r = new ArrayList<>();
		start = start.toUpperCase();
		for (Material m : Material.values()) { 
			if (m.toString().startsWith(start)) {
				String s = m.toString().toLowerCase();
				if (prefix != null) s = prefix + s;
				r.add(s);
			}
		}
		return r;
	}
	
	//Using for faster implementation: https://stackoverflow.com/questions/9655181/java-convert-a-byte-array-to-a-hex-string
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for (int j = 0; j < bytes.length; j++) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	//https://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
	public static byte[] hexStringToBytes(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	//End Citation
	
	/**
	 * Removes any 'temp' plus random value
	 * @param label
	 * @return
	 */
	public static String getEffectiveLabel(String label) {
		StringBuilder dispLabelSB = new StringBuilder();
		String[] labelSplit = label.split("-");
		for (int i = 0; i < labelSplit.length; i++) {
			if (i > 0) {
				if (labelSplit[i].equals("temp")) break;
				dispLabelSB.append('-');
			}
			dispLabelSB.append(labelSplit[i]);
		}
		return dispLabelSB.toString();
	}
	
	public static double getParameter(Vector v, int axis) {
		switch(axis) {
		case 0: return v.getX();
		case 1: return v.getY();
		case 2: return v.getZ();
		default: return getParameter(v, axis % 3);
		}
	}
	
	public static void setParameter(Vector v, int axis, double val) {
		switch(axis) {
		case 0: 
			v.setX(val);
			return;
		case 1: 
			v.setY(val);
			return;
		case 2: 
			v.setZ(val);
			return;
		default: setParameter(v, axis % 3, val);
		}
	}
	
	public static double getParameter(Location loc, int axis) {
		switch(axis) {
		case 0: return loc.getX();
		case 1: return loc.getY();
		case 2: return loc.getZ();
		default: return getParameter(loc, axis % 3);
		}
	}
	
	public static Vector oneHot(int axis) {
		return oneHot(axis, 1);
	}
	
	public static Vector oneHot(int axis, double param) {
		switch(axis) {
		case 0: return new Vector(param, 0, 0);
		case 1: return new Vector(0, param, 0);
		case 2: return new Vector(0, 0, param);
		default: return oneHot(axis % 3, param);
		}
	}
	
	public static boolean numbersContain(List<Double> list, double x) { //unsorted list
		double epsilon = 0.00001;
		for (double i : list) {
			if (Math.abs(i - x) < epsilon) return true;
		}
		return false;
	}
	
	public static Matrix3d rotMatDeg(double xdeg, double ydeg, double zdeg) {
		return rotMat(Math.toRadians(xdeg), Math.toRadians(ydeg), Math.toRadians(zdeg));
	}
	
	public static Matrix3d rotMat(double xrad, double yrad, double zrad) {
		double sinx = Math.sin(xrad), siny = -Math.sin(yrad), sinz = Math.sin(zrad);
		double cosx = Math.cos(xrad), cosy = Math.cos(yrad), cosz = Math.cos(zrad);
//		return new Matrix3d( //(Rz)(Ry)(Rx)
//				cosz*cosy, sinz*cosy, -siny,
//				(cosz*siny*sinx) - (sinz*cosx), (sinz*siny*sinx) + (cosz*cosx), cosy*sinx,
//				(cosz*siny*cosx) + (sinz*siny), (sinz*siny*cosx) - (cosz*sinx), cosy*cosx
//				);
		return new Matrix3d( //(Rz)(Rx)(Ry)
				cosy*cosz - sinx*siny*sinz, sinx*siny*cosz + cosy*sinz, -cosx*siny,
				-cosx*sinz, cosx*cosz, sinx,
				sinx*cosy*sinz + siny*cosz, siny*sinz - sinx*cosy*cosz, cosx*cosy
				);
	}
	
	public static boolean isAllowedMaterial(Material mat) {
		switch(mat) {
		case AIR:
		case WATER:
		case WATER_BUCKET:
		case LAVA:
		case LAVA_BUCKET:
		case BARRIER:
		case END_PORTAL:
		case PLAYER_HEAD:
		case CREEPER_HEAD:
		case ZOMBIE_HEAD:
		case PIGLIN_HEAD:
		case DRAGON_HEAD:
		case STRUCTURE_VOID:
		case LIGHT:
			return false;
		default: return true;
		}
	}
	
	/**
	 * Get the integer version of the server's minecraft version Examples: MC 1.8.8
	 * -> 8 MC 1.21.6 -> 21 MC 26.1.1 -> 26
	 * 
	 * @return
	 */
	public static int getServerVersionNumber() {
		// Example str:
		// 4616-Spigot-4a90bec-48244d7 (MC: 26.1.1)
		// 1.21.6-48-4d854e6 (MC: 1.21.6)
		int version = Dexterity.MIN_NONLEGACY_SERVER_VERSION;

		String rawVersionStr = Bukkit.getVersion();
		String versionStr = getStringBetween(rawVersionStr, "MC: ", ')');
		if (versionStr == null)
			return version;

		if (versionStr.startsWith("1.")) { // ex. 1.21.6, 1.8.8
			String numStr = getStringBetween(versionStr, "1.", '.');
			try {
				version = Integer.parseInt(numStr);
			} catch (NumberFormatException ex) {

			}
		} else {
			int firstDot = versionStr.indexOf('.');
			String numStr;
			if (firstDot == -1) numStr = versionStr;
			else numStr = versionStr.substring(0, firstDot);
			
			try {
				version = Integer.parseInt(numStr);
			} catch (NumberFormatException ex) {
			}
		}

		return version;
	}

	private static String getStringBetween(String str, String start, char endChar) {
		int startIndex = str.indexOf(start);
		if (startIndex == -1) return null;
		int strStart = startIndex + start.length();
		int endIndex = str.substring(strStart).indexOf(endChar);
		if (endIndex == -1) return str.substring(startIndex);

		int strEnd = strStart + endIndex;
		return str.substring(strStart, strEnd);
	}
	
	public static boolean isOrthonormal(Vector x, Vector y) {
		double epsilon = 0.00000001;
		return x.dot(y) < epsilon && x.length() - 1 < epsilon && y.length() - 1 < epsilon;
	}
	
	public static boolean isOrthonormal(Vector x, Vector y, Vector z) {
		double epsilon = 0.00000001;
		return x.dot(y) < epsilon && y.dot(z) < epsilon && z.dot(x) < epsilon && 
				Math.abs(x.length() - 1) < epsilon && Math.abs(y.length() - 1) < epsilon && Math.abs(z.length() - 1) < epsilon;
	}
	
	public static Vector nearestPoint(Vector a, Vector b, Vector x) { //nearest point to x on line defined by a, b
		Vector b_a = b.clone().subtract(a);
		double theta = b_a.dot(x.clone().subtract(a)) / b_a.lengthSquared();
		return a.clone().multiply(1-theta).add(b.clone().multiply(theta));
	}
	
	public static double clamp(double num, double min, double max) {
		if (num < min) return min;
		if (num > max) return max;
		return num;
	}

}
