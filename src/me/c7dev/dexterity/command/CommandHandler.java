package me.c7dev.dexterity.command;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import me.c7dev.dexterity.DexSession;
import me.c7dev.dexterity.DexSession.AxisType;
import me.c7dev.dexterity.DexSession.EditType;
import me.c7dev.dexterity.Dexterity;
import me.c7dev.dexterity.api.DexRotation;
import me.c7dev.dexterity.api.DexterityAPI;
import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.displays.animation.Animation;
import me.c7dev.dexterity.displays.animation.RideableAnimation;
import me.c7dev.dexterity.displays.animation.SitAnimation;
import me.c7dev.dexterity.displays.schematics.Schematic;
import me.c7dev.dexterity.displays.schematics.SchematicBuilder;
import me.c7dev.dexterity.transaction.BlockTransaction;
import me.c7dev.dexterity.transaction.BuildTransaction;
import me.c7dev.dexterity.transaction.ConvertTransaction;
import me.c7dev.dexterity.transaction.DeconvertTransaction;
import me.c7dev.dexterity.transaction.RemoveTransaction;
import me.c7dev.dexterity.transaction.RotationTransaction;
import me.c7dev.dexterity.transaction.ScaleTransaction;
import me.c7dev.dexterity.transaction.Transaction;
import me.c7dev.dexterity.util.ClickedBlockDisplay;
import me.c7dev.dexterity.util.ColorEnum;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexTransformation;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.DexterityException;
import me.c7dev.dexterity.util.InteractionCommand;
import me.c7dev.dexterity.util.Mask;
import me.c7dev.dexterity.util.RotationPlan;

/**
 * Implementation of all in-game /dex commands
 */
public class CommandHandler {
	
	private Dexterity plugin;
	private DexterityAPI api;
	private String noperm, cc, cc2, usageFormat, selectedStr, loclabelPrefix;
	
	public CommandHandler(Dexterity plugin) {
		this.plugin = plugin;
		
		cc = plugin.getChatColor();
		cc2 = plugin.getChatColor2();
		api = plugin.api();
		noperm = plugin.getConfigString("no-permission");
		usageFormat = plugin.getConfigString("usage-format");
		selectedStr = plugin.getConfigString("selected");
		loclabelPrefix = plugin.getConfigString("loclabel-prefix", "selection at");
	}
	
	public boolean withPermission(Player p, String perm) {
		if (p.hasPermission("dexterity.command." + perm)) return true;
		else {
			if (perm.startsWith("schematic")) return withPermission(p, perm.replaceAll("schematic", "schem")); //remap for legacy
			p.sendMessage(noperm);
			return false;
		}
	}
	
	public DexterityDisplay getSelected(DexSession session, String... perms) {
		if (perms != null && perms.length > 0) {
			boolean auth = false;
			for (String perm : perms) {
				if (session.getPlayer().hasPermission("dexterity.command." + perm)) {
					auth = true;
					break;
				}
			}
			if (!auth) {
				session.getPlayer().sendMessage(noperm);
				return null;
			}
		}

		DexterityDisplay d = session.getSelected();
		if (d == null) {
			session.getPlayer().sendMessage(plugin.getConfigString("must-select-display"));
			return null;
		}
		return d;
	}
	
	public boolean testInEdit(DexSession session) {
		if (session.getEditType() != null) {
			session.getPlayer().sendMessage(plugin.getConfigString("must-finish-edit"));
			return true;
		}
		return false;
	}
	
	public List<String> listSchematics() {
		List<String> r = new ArrayList<>();
		try {
			File f = new File(plugin.getDataFolder().getAbsolutePath() + "/schematics");
			for (File sub : f.listFiles()) {
				String name = sub.getName().toLowerCase();
				if (name.endsWith(".dex") || name.endsWith(".dexterity")) {
					r.add(name.replaceAll("\\.dexterity|\\.dex", ""));
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return r;
	}
	
	public int constructList(String[] strs, DexterityDisplay disp, String selected, int i, int level) {
		if (disp.getLabel() == null) return 0;
		int count = 1;
		
		String line = "";
		for (int j = 0; j < level; j++) line += "  ";
		
		line += disp.getLabel();
		if (disp.getBlocksCount() > 0) line = cc2 + ((selected != null && disp.getLabel().equals(selected)) ? "§d" : "") + line + "§7: " + cc + DexUtils.locationString(disp.getCenter(), 0) + " (" + disp.getCenter().getWorld().getName() + ")";
		else line = cc2 + ((selected != null && disp.getLabel().equals(selected)) ? "§d" : "") + "§l" + line + cc + ":";
		
		strs[i] = line;
		
		DexterityDisplay[] subs = disp.getSubdisplays();
		for (int j = 1; j <= subs.length; j++) {
			count += constructList(strs, subs[j-1], selected, i+count, level+1);
		}
		
		return count;
	}
	
	public String getUsage(String command) {
		String usage = plugin.getConfigString(command + "-usage");
		return usageFormat.replaceAll("\\Q%usage%\\E", usage);
	}
	
	public String getConfigString(String dir, DexSession session) {
		return getConfigString(dir, session, null);
	}
	
	public String getConfigString(String dir, DexSession session, String override_label) {
		String s = plugin.getConfigString(dir);
		if (override_label != null) {
			s = s.replaceAll("\\Q%label%\\E", override_label).replaceAll("\\Q%loclabel%\\E", override_label);
		}
		else if (session != null && session.getSelected() != null && session.getSelected().getLabel() != null) {
			String label = cc2 + session.getSelected().getLabel() + cc;
			s = s.replaceAll("\\Q%label%\\E", label).replaceAll("\\Q%loclabel%\\E", label); //regex substr selector isn't working, idk
		} 
		else {
			s = s.replaceAll("\\Q%label%\\E", selectedStr);
			if (session != null && session.getSelected() != null) s = s.replaceAll("\\Q%loclabel%", loclabelPrefix + " " + cc2 + DexUtils.locationString(session.getSelected().getCenter(), 0) + cc);
			else s = s.replaceAll("\\Q%loclabel%\\E", "");
		}
		return s;
	}
	
	public void help(CommandContext ctx, String[] commands_str) {
		int page = 0;
		HashMap<String, Integer> attrs = ctx.getIntAttrs();
		if (attrs.containsKey("page")) {
			page = Math.max(attrs.get("page") - 1, 0);
		} else if (ctx.getArgs().length >= 2) page = Math.max(DexUtils.parseInt(ctx.getArgs()[1]) - 1, 0);
		int maxpage = DexUtils.maxPage(commands_str.length, 5);
		if (page >= maxpage) page = maxpage - 1;

		ctx.getPlayer().sendMessage(plugin.getConfigString("help-page-header").replaceAll("\\Q%page%\\E", "" + (page+1)).replaceAll("\\Q%maxpage%\\E", "" + maxpage));
		DexUtils.paginate(ctx.getPlayer(), commands_str, page, 5);
	}
	
	public void wand(CommandContext ct) {
		if (!withPermission(ct.getPlayer(), "wand")) return;
		ItemStack wand = new ItemStack(plugin.getWandType());
		ItemMeta meta = wand.getItemMeta();
		meta.setDisplayName(plugin.getConfigString("wand-title", "§fDexterity Wand"));
		wand.setItemMeta(meta);
		ct.getPlayer().getInventory().addItem(wand);
	}
	
	public void debugCenters(CommandContext ct) {
		if (!ct.getPlayer().hasPermission("dexterity.admin")) return;
		DexterityDisplay d = getSelected(ct.getSession());
		if (d == null) return;
		boolean entity_centers = ct.getFlags().contains("entities");
		for (DexBlock db : d.getBlocks()) {
			api.markerPoint(db.getLocation(), Math.abs(db.getRoll()) < 0.000001 ? Color.LIME : Color.AQUA, 6);
			if (entity_centers) api.markerPoint(db.getEntity().getLocation(), Color.ORANGE, 6);
		}
	}
	
	public void debugRemoveTransformation(CommandContext ct) {
		if (!ct.getPlayer().hasPermission("dexterity.admin")) return;
		DexterityDisplay d = getSelected(ct.getSession());
		if (d == null) return;
		for (DexBlock db : d.getBlocks()) {
			db.getEntity().setTransformation(new DexTransformation(db.getEntity().getTransformation()).setDisplacement(new Vector(0, 0, 0)).setRollOffset(new Vector(0, 0, 0)).build());
			api.markerPoint(db.getEntity().getLocation(), Color.AQUA, 2);
		}
	}
	
	public void debugResetTransformation(CommandContext ct) {
		if (!ct.getPlayer().hasPermission("dexterity.admin")) return;
		DexterityDisplay d = getSelected(ct.getSession());
		if (d == null) return;
		for (DexBlock db : d.getBlocks()) {
			db.loadTransformationAndRoll();
			api.markerPoint(db.getLocation(), Color.AQUA, 2);
		}
		d.recalculateCenter();
	}
	
	public void debugTestNear(CommandContext ct) {
		if (!ct.getPlayer().hasPermission("dexterity.admin")) return;
		ClickedBlockDisplay b = api.getLookingAt(ct.getPlayer());
		if (b == null) ct.getPlayer().sendMessage(cc + "None in range");
		else {
			api.markerPoint(b.getClickLocation(), Color.RED, 4);
			ct.getPlayer().sendMessage(cc + "Clicked " + b.getBlockFace() + ", " + b.getBlockDisplay().getBlock().getMaterial());
		}
	}
	
	public void debugKill(CommandContext ct) {
		if (!ct.getPlayer().hasPermission("dexterity.admin")) return;
		HashMap<String, Double> attrs_d = ct.getDoubleAttrs();
		if (!attrs_d.containsKey("radius") && !attrs_d.containsKey("r")) {
			ct.getPlayer().sendMessage(plugin.getConfigString("must-enter-value").replaceAll("\\Q%value%\\E", "radius"));
			return;
		}
		double radius = attrs_d.getOrDefault("radius", attrs_d.get("r"));
		double minScale = attrs_d.getOrDefault("min_scale", Double.MIN_VALUE), maxScale = attrs_d.getOrDefault("max_scale", Double.MAX_VALUE);
		
		List<Entity> entities = ct.getPlayer().getNearbyEntities(radius, radius, radius);
		for (Entity e : entities) {
			if (!(e instanceof BlockDisplay)) continue;
			BlockDisplay bd = (BlockDisplay) e;
			Vector3f scale = bd.getTransformation().getScale();
			if (scale.x < minScale || scale.x > maxScale
					|| scale.y < minScale || scale.y > maxScale 
					|| scale.z < minScale || scale.z > maxScale) continue;
			e.remove();
		}
	}
	
	public void debugPurgeUnloaded(CommandContext ct) {
		if (!ct.getPlayer().hasPermission("dexterity.admin")) return;
		plugin.purgeUnloadedDisplays();
	}
	
	public void debugItem(CommandContext ct) {
		if (!ct.getPlayer().hasPermission("dexterity.admin")) return;
		Player p = ct.getPlayer();
		ItemStack hand = ct.getPlayer().getInventory().getItemInMainHand();
		if (hand == null || hand.getType() == Material.AIR) {
			p.sendMessage(plugin.getConfigString("must-hold-item"));
			return;
		}
		
		ItemMeta meta = hand.getItemMeta();
		NamespacedKey key = new NamespacedKey(plugin, "dex-schem-label");
		String schem_name = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		
		if (schem_name == null) p.sendMessage("§cThere is no Dexterity schematic associated with this item!");
		else p.sendMessage(cc + "This item is tied to the " + cc2 + schem_name + cc + " item schematic.");
	}
	
	public void paste(CommandContext ct) {
		DexSession session = ct.getSession();
		if (session.getEditType() != null) {
			switch(session.getEditType()) {
			case CLONE_MERGE:
				if (session.getSecondary() != null) {
					session.getSecondary().hardMerge(session.getSelected());
				}
			case CLONE:
				ct.getPlayer().sendMessage(getConfigString("clone-success", session));
				break;
			default:
			}
			session.finishEdit();
		}
	}
	
	public void consolidate(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "consolidate");
		if (d == null) return;
		
		Mask mask = session.getMask();
		
		if (ct.getDefaultArg() != null) {
			try {
				Material mat = Material.valueOf(ct.getDefaultArg().toUpperCase().trim());
				mask = new Mask(mat);
			} catch (Exception ex) {
				ct.getPlayer().sendMessage(getConfigString("unknown-material", session).replaceAll("\\Q%input%\\E", ct.getDefaultArg().toLowerCase()));
				return;
			}
		}
		
		BlockTransaction t = new BlockTransaction(d, mask);
		d.consolidate(mask, t);
		session.pushTransaction(t); //commit is async
		
		ct.getPlayer().sendMessage(getConfigString("consolidate-success", session));
	}
	
	public void recenter(CommandContext ct) { //TODO: add -auto to recalculate
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "recenter");
		if (d == null) return;
		
		if (!d.getCenter().getWorld().getName().equals(p.getLocation().getWorld().getName())) {
			p.sendMessage(getConfigString("must-same-world", session));
			return;
		}
		
		if (ct.getFlags().contains("auto") || ct.getFlags().contains("reset")) {
			BlockTransaction t = new BlockTransaction(d);
			t.commitCenter(d.getCenter());
			t.commitEmpty();
			
			d.recalculateCenter(false);
			api.markerPoint(d.getCenter(), Color.AQUA, 4);
			
			session.pushTransaction(t);
			p.sendMessage(getConfigString("recenter-auto-success", session));
		} else {
			Location loc = p.getLocation();
			if (!ct.getFlags().contains("continuous")) DexUtils.blockLoc(loc).add(0.5, 0.5, 0.5);

			BlockTransaction t = new BlockTransaction(d);
			t.commitCenter(loc);
			t.commitEmpty();

			d.setCenter(loc);
			api.markerPoint(loc, Color.AQUA, 4);

			session.pushTransaction(t);

			p.sendMessage(getConfigString("recenter-success", session));
		}
	}
	
	public void align(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "move");
		if (d == null) return;
		
		boolean to_center = ct.getFlags().contains("center"), 
				x = ct.getFlags().contains("x") || ct.getDefaultArgs().contains("x"),
				y = ct.getFlags().contains("y") || ct.getDefaultArgs().contains("y"),
				z = ct.getFlags().contains("z") || ct.getDefaultArgs().contains("z");
		
		if (!(x || y || z)) { //no axis flags passed
			x = true;
			y = true;
			z = true;
		}
		
		BlockTransaction t = new BlockTransaction(d);
		d.align(to_center, x, y, z);
		t.commit(d.getBlocks());
		session.pushTransaction(t);
		
		if (session.getFollowingOffset() != null) {
			Location loc2 = ct.getPlayer().getLocation();
			if (!ct.getPlayer().isSneaking()) DexUtils.blockLoc(loc2);
			session.setFollowingOffset(d.getCenter().toVector().subtract(loc2.toVector()));
		}
		
		ct.getPlayer().sendMessage(getConfigString("align-success", session));
	}
	
	public void axis(CommandContext ct) {
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "axis");
		String[] args = ct.getArgs();
		if (d == null) return;
		
		if (args.length == 1 || args[1].equalsIgnoreCase("show")) {
			if (!p.hasPermission("dexterity.axis.show")) {
				p.sendMessage(noperm);
				return;
			}
			
			if (args.length <= 2) session.setShowingAxes(session.getShowingAxisType() == null ? AxisType.SCALE : null); 
			else {
				if (args[2].equalsIgnoreCase("rotation")) session.setShowingAxes(AxisType.ROTATE);
				else if (args[2].equalsIgnoreCase("scale")) session.setShowingAxes(AxisType.SCALE);
				else {
					p.sendMessage(plugin.getConfigString("unknown-input").replaceAll("\\Q%input%\\E", args[2]));
					return;
				}
			}
		}
		else if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reset")) {
			HashMap<String, Double> attrs_d = ct.getDoubleAttrs();
			boolean setScale = args.length >= 3 && args[2].equals("scale");
			double defaultParam = setScale ? 1d : 0d;
			double x = Math.abs(attrs_d.getOrDefault("x", attrs_d.getOrDefault("pitch", defaultParam))),
					y = Math.abs(attrs_d.getOrDefault("y", attrs_d.getOrDefault("yaw", defaultParam))), 
					z = Math.abs(attrs_d.getOrDefault("z", attrs_d.getOrDefault("roll", defaultParam)));
			
			if (setScale) {
				Vector currScale = d.getScale();
				if (x == 0) x = currScale.getX();
				if (y == 0) y = currScale.getY();
				if (z == 0) z = currScale.getZ();
				
				if (x == 0 || y == 0 || z == 0) {
					p.sendMessage(plugin.getConfigString("must-send-number"));
					return;
				}
				ScaleTransaction t = new ScaleTransaction(d);
				d.resetScale(new Vector(x, y, z));
				t.commitEmpty();
				session.pushTransaction(t);
				session.updateAxisDisplays();
				
				p.sendMessage(getConfigString("axis-set-success", session).replaceAll("\\Q%type%\\E", "scale"));
			} else if (!setScale || args[1].equalsIgnoreCase("reset")) {
				RotationTransaction t = new RotationTransaction(d);
				d.setBaseRotation((float) y, (float) x, (float) z);
				t.commitEmpty();
				session.pushTransaction(t);
				session.updateAxisDisplays();
				
				p.sendMessage(getConfigString("axis-set-success", session).replaceAll("\\Q%type%\\E", "rotation"));
			} else {
				p.sendMessage(plugin.getConfigString("unknown-input").replaceAll("\\Q%input%\\E", args[2]));
				return;
			}
			
		}
		else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("hide")) {
			session.setShowingAxes(null);
		}
		else p.sendMessage(plugin.getConfigString("unknown-input").replaceAll("\\Q%input%\\E", args[1].toLowerCase()));
	}
	
	public void reload(CommandContext ct) {
		if (!withPermission(ct.getPlayer(), "admin")) return;
		
		if (ct.getFlags().contains("saved_displays")) plugin.reloadDisplays();
		else plugin.reload();
		
		ct.getPlayer().sendMessage(plugin.getConfigString("reload-success"));
	}
	
	public void replace(CommandContext ct) {
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		String[] args = ct.getArgs();
		DexterityDisplay d = getSelected(session, "replace");
		if (d == null) return;
		
		if (args.length >= 3) {
			Material from, to;
			try {
				from = Material.valueOf(args[1].toUpperCase());
			} catch (Exception ex) {
				p.sendMessage(getConfigString("unknown-material", session).replaceAll("\\Q%input%\\E", args[1].toLowerCase()));
				return;
			}
			try {
				to = Material.valueOf(args[2].toUpperCase());
			} catch (Exception ex) {
				p.sendMessage(getConfigString("unknown-material", session).replaceAll("\\Q%input%\\E", args[2].toLowerCase()));
				return;
			}
			
			Mask mask = new Mask(from);
			BlockTransaction t = new BlockTransaction(d, mask);
			if (to == Material.AIR) {
				for (DexBlock db : d.getBlocks()) if (db.getEntity().getBlock().getMaterial() == from) db.remove();
				t.commit(d.getBlocks(), mask, true);
			} else {
				BlockData todata = Bukkit.createBlockData(to);
				for (DexBlock db : d.getBlocks()) {
					if (db.getEntity().getBlock().getMaterial() == from) {
						if (!plugin.isLegacy()) db.getEntity().getBlock().copyTo(todata);
						db.getEntity().setBlock(todata);
						t.commitBlock(db);
					}
				}
			}
			
			if (t.isCommitted()) session.pushTransaction(t);
			
			p.sendMessage(getConfigString("replace-success", session)
					.replaceAll("\\Q%from%\\E", from.toString().toLowerCase())
					.replaceAll("\\Q%to%\\E", to.toString().toLowerCase()));
			
		} else p.sendMessage(getUsage("replace"));
	}
	
	public void select(CommandContext ct) {
		int index = -1;
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		String[] args = ct.getArgs();
		String def = ct.getDefaultArg();
		if (args[0].equals("pos1")) index = 0;
		else if (args[0].equals("pos2")) index = 1;
		
		if (index < 0) {
			if (args.length == 1) {
				p.sendMessage(getUsage("sel"));
				return;
			}
		}
		if (def != null) {
			DexterityDisplay disp = plugin.getDisplay(def);
			if (disp != null) {	
				session.setSelected(disp, true);
				return;
			} else if (index < 0) {
				p.sendMessage(plugin.getConfigString("display-not-found").replaceAll("\\Q%input%\\E", def));
				return;
			}
		}

		Location loc = p.getLocation();
		
		if (args.length == 4) {
			try {
				double x = Double.parseDouble(args[1]);
				double y = Double.parseDouble(args[2]);
				double z = Double.parseDouble(args[3]);
				loc = new Location(p.getWorld(), x, y, z, 0, 0);
				index = -1;
			} catch (Exception ex) {
				p.sendMessage(plugin.getConfigString("must-send-numbers-xyz"));
				return;
			}
		}
		session.setContinuousLocation(loc, index == 0, new Vector(0, 0, 0), true);
	}
	
	public void deselect(CommandContext ct) {
		DexSession session = ct.getSession();
		if (session.getSelected() != null) {
			session.setSelected(null, false);
			session.clearLocationSelection();
			ct.getPlayer().sendMessage(plugin.getConfigString("desel-success"));
		}
	}
	
	public void highlight(CommandContext ct) {
		DexterityDisplay d = getSelected(ct.getSession(), "highlight");
		if (d == null) return;
		api.tempHighlight(d, 50, Color.ORANGE);
	}
	
	public void clone(CommandContext ct) {
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		String def = ct.getDefaultArg();
		
		if (!withPermission(p, "clone") || testInEdit(session)) return;
		DexterityDisplay d = session.getSelected();
		if (d == null && def == null) {
			p.sendMessage(plugin.getConfigString("must-select-display"));
			return;
		}
		
		if (def != null) {
			d = plugin.api().getDisplay(def);
			if (d == null) {
				p.sendMessage(plugin.getConfigString("display-not-found").replaceAll("\\Q%input%\\E", def));
				return;
			}
			session.setSelected(d, false);
		}
		
		boolean mergeafter = ct.getFlags().contains("merge"), nofollow = ct.getFlags().contains("nofollow");
		if (mergeafter && !nofollow && !d.canHardMerge()) {
			p.sendMessage(getConfigString("cannot-clone", session));
			return;
		}
		
		DexterityDisplay clone = api.clone(d);
		
		if (!clone.getCenter().getWorld().getName().equals(p.getWorld().getName()) || clone.getCenter().distance(p.getLocation()) >= 80) clone.teleport(p.getLocation());
		
		if (nofollow) {
			p.sendMessage(getConfigString("clone-success", session));
			session.setSelected(clone, false);
		} else {
			p.sendMessage(getConfigString("to-finish-edit", session));
			session.startEdit(clone, mergeafter ? EditType.CLONE_MERGE : EditType.CLONE, true);
			session.startFollowing();
		}
	}
	
	public void owner(CommandContext ct) {
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		String[] args = ct.getArgs();
		DexterityDisplay d = getSelected(session, "owner");
		if (d == null) return;
		if (!d.isSaved()) {
			p.sendMessage(getConfigString("not-saved", session));
			return;
		}
		
		if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
			int page = 0;
			HashMap<String, Integer> attrs = ct.getIntAttrs();
			if (attrs.containsKey("page")) page = Math.max(attrs.get("page") - 1, 0);
			
			List<String> owners_str = new ArrayList<>();
			OfflinePlayer[] owners = d.getOwners();
			if (owners.length == 0) owners_str.add(cc + "- " + cc2 + "*");
			else {
				for (OfflinePlayer owner : owners) owners_str.add(cc + "- " + cc2 + owner.getName());
			}
			
			int maxpage = DexUtils.maxPage(owners_str.size(), 5);
			if (page >= maxpage) page = maxpage - 1;
			p.sendMessage(plugin.getConfigString("owner-list-header").replaceAll("\\Q%page%\\E", "" + (page+1)).replaceAll("\\Q%maxpage%\\E", "" + maxpage));
			DexUtils.paginate(p, owners_str.toArray(new String[owners_str.size()]), page, 5);
		}
		else if (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")) {
			boolean adding = args[1].equalsIgnoreCase("add");
			if (args.length <= 2) {
				p.sendMessage(getUsage("owner-" + (adding ? "add" : "remove")));
				return;
			}
			OfflinePlayer owner = Bukkit.getOfflinePlayer(args[2]);
			if (owner == null) {
				p.sendMessage(plugin.getConfigString("player-not-found").replaceAll("\\Q%player%\\E", args[2]));
				return;
			}
			
			if (adding) d.addOwner(owner);
			else d.removeOwner(owner);
			p.sendMessage(getConfigString(adding ? "owner-add-success" : "owner-remove-success", session).replaceAll("\\Q%player%\\E", owner.getName()));
			if (d.getOwners().length == 0) p.sendMessage(getConfigString("owner-remove-success-warning", session));
		}
		else p.sendMessage("unknown-subcommand");
	}
	
	public void tile(CommandContext ct) {
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "tile");
		String def = ct.getDefaultArg();
		if (d == null) return;
		
		Vector delta = new Vector();
		HashMap<String, Double> attrs_d = ct.getDoubleAttrs();
		int count = Math.abs(attrs_d.getOrDefault("count", 0d).intValue());
		
		if (count == 0) { //check valid count
			if (def != null) {
				try {
					count = Integer.parseInt(def);
				} catch(Exception ex) {
					p.sendMessage(plugin.getConfigString("must-enter-value").replaceAll("\\Q%value%\\E", "count"));
					return;
				}
			}
			else {
				p.sendMessage(plugin.getConfigString("must-enter-value").replaceAll("\\Q%value%\\E", "count"));
				return;
			}
		}
		
		if (d.getBlocksCount()*(count+1) > session.getPermittedVolume()) { //check volume
			p.sendMessage(plugin.getConfigString("exceeds-max-volume").replaceAll("\\Q%volume%\\E", "" + (int) session.getPermittedVolume()));
			return;
		}
		
		if (attrs_d.containsKey("x")) delta.setX(attrs_d.get("x"));
		if (attrs_d.containsKey("y")) delta.setY(attrs_d.get("y"));
		if (attrs_d.containsKey("z")) delta.setZ(attrs_d.get("z"));
		if (attrs_d.containsKey("rx") || attrs_d.containsKey("ry") || attrs_d.containsKey("rz") || def != null) {
			DexRotation rot = d.getRotationManager(true);
			if (attrs_d.containsKey("rx")) delta.add(rot.getXAxis().multiply(attrs_d.get("rx")));
			if (attrs_d.containsKey("ry")) delta.add(rot.getYAxis().multiply(attrs_d.get("ry")));
			if (attrs_d.containsKey("rz")) delta.add(rot.getZAxis().multiply(attrs_d.get("rz")));
		}
		
		if (delta.getX() == 0 && delta.getY() == 0 && delta.getZ() == 0) { //cannot be 0 delta
			p.sendMessage(plugin.getConfigString("must-send-numbers-xyz"));
			return;
		}
		
		Location loc = d.getCenter();
		DexterityDisplay toMerge = new DexterityDisplay(plugin, d.getCenter(), d.getScale());
		BuildTransaction t = new BuildTransaction(d);
		Vector centerv = d.getCenter().toVector();
		
		for (int i = 0; i < count; i++) {
			loc.add(delta);
			DexterityDisplay c = api.clone(d);
			for (DexBlock db : c.getBlocks()) {
				Vector diff = new Vector(loc.getX() - centerv.getX(), loc.getY() - centerv.getY(), loc.getZ() - centerv.getZ());
				db.move(diff);
				t.addBlock(db);
			}
			toMerge.hardMerge(c);
		}
		
		d.hardMerge(toMerge);
		t.commit();
		session.pushTransaction(t);
		
		p.sendMessage(getConfigString("tile-success", session));
	}
	
	public void cancel(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session);
		if (d == null) return;
		session.cancelEdit();
		ct.getPlayer().sendMessage(getConfigString("cancelled-edit", session));
	}
	
	public void glow(CommandContext ct) { //TODO: add transaction
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "glow");
		if (d == null) return;
		
		String[] args = ct.getArgs();
		String def = ct.getDefaultArg();
		Player p = ct.getPlayer();
		
		boolean propegate = false; //flags.contains("propegate");
		if (args.length < 2 || (def != null && (def.equals("none") || def.equals("off"))) || ct.getFlags().contains("none") || ct.getFlags().contains("off")) {
			d.setGlow(null, propegate);
			p.sendMessage(getConfigString("glow-success-disable", session));
			return;
		}
		
		ColorEnum c;
		try {
			c = ColorEnum.valueOf(def.toUpperCase());
		} catch (Exception ex) {
			p.sendMessage(getConfigString("unknown-color", session).replaceAll("\\Q%input%\\E", args[1].toUpperCase()));
			return;
		}
		d.setGlow(c.getColor(), propegate);
		if (d.getLabel() != null) p.sendMessage(getConfigString("glow-success", session));
	}
	
	public void seat(CommandContext ct) {
		DexSession session = ct.getSession();
		Player p = ct.getPlayer();
		DexterityDisplay d = getSelected(session, "seat");
		if (d == null) return;
		if (!d.isSaved()) {
			p.sendMessage(getConfigString("not-saved", session));
			return;
		}
		
		Animation anim = d.getAnimation(RideableAnimation.class);
		
		if (anim == null) {
			HashMap<String, Double> attrs_d = ct.getDoubleAttrs();
			double y_offset = attrs_d.getOrDefault("y_offset", 0d);
			
			SitAnimation a = new SitAnimation(d);
			if (y_offset != 0) a.setSeatOffset(new Vector(0, y_offset, 0));
			d.addAnimation(a);
			p.sendMessage(getConfigString("seat-success", session));
		} else {
			d.removeAnimation(anim);
			p.sendMessage(getConfigString("seat-disable-success", session));
		}
	}
	
	public void command(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "cmd");
		if (d == null) return;
		
		Player p = ct.getPlayer();
		List<String> defs = ct.getDefaultArgs();
		String[] args = ct.getArgs();
		List<String> flags = ct.getFlags();
		
		if (args.length <= 1) {
			p.sendMessage(getUsage("cmd"));
			return;
		}
			
		if (args[1].equalsIgnoreCase("add")) {
			if (defs.size() == 1 || args.length < 2){
				p.sendMessage(getUsage("cmd-add"));
				return;
			}
			if (!d.isSaved()) {
				p.sendMessage(getConfigString("not-saved", session));
				return;
			}

			StringBuilder cmd_strb = new StringBuilder();
			boolean appending = false;
			for (int i = 2; i < args.length; i++) {
				String arg = args[i];
				if (!appending && !arg.contains("=") && !arg.startsWith("-") && !arg.contains(":")) appending = true;
				if (appending) {
					cmd_strb.append(arg);
					cmd_strb.append(" ");
				}
			}
			String cmd_str = cmd_strb.toString().trim();
			if (cmd_str.length() == 0) {
				p.sendMessage("cmd-add");
				return;
			}
			InteractionCommand command = new InteractionCommand(cmd_str);

			//set flags
			if (flags.contains("left_only") || flags.contains("r")) {
				command.setLeft(true);
				command.setRight(false);
			} else if (flags.contains("right_only") || flags.contains("l")) {
				command.setLeft(false);
				command.setRight(true);
			}
			
			boolean by_player = flags.contains("player") || flags.contains("p") || !p.hasPermission("dexterity.command.cmd.console");
			command.setByPlayer(by_player);
			
			HashMap<String, String> attr_str = ct.getStringAttrs();
			if (attr_str.containsKey("permission")) command.setPermission(attr_str.get("permission"));

			d.addCommand(command);
			p.sendMessage(getConfigString("cmd-add-success", session).replaceAll("\\Q%id%\\E", "" + d.getCommandCount()));
		}
		
		else if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("rem")) {
			if (args.length < 3) {
				p.sendMessage(getUsage("cmd-remove"));
				return;
			}
			
			int index;
			try {
				index = Integer.parseInt(args[2]);
				if (index != 0) index -= 1;
			} catch (Exception ex) {
				p.sendMessage(getConfigString("must-send-number", session));
				return;
			}
			if (index < 0) index = d.getCommandCount() + index + 1; //ex. input -1 for the last command
			if (index < d.getCommandCount()) {
				InteractionCommand command = d.getCommands()[index];
				d.removeCommand(command);
			}
			p.sendMessage(getConfigString("cmd-remove-success", session).replaceAll("\\Q%id%\\E", (index+1) + ""));			
		}
		
		else if (args[1].equalsIgnoreCase("list")) {
			if (d.getCommandCount() == 0) p.sendMessage(getConfigString("list-empty", session));
			else {
				InteractionCommand[] cmds = d.getCommands();
				for (int i = 0; i < cmds.length; i++) {
					p.sendMessage(cc2 + (i+1) + "." + cc + " " + cmds[i].getCmd());
				}
			}
		}
		
		else p.sendMessage(getUsage("cmd"));
	}
	
	public void convert(CommandContext ct) {
		DexSession session = ct.getSession();
		Player p = ct.getPlayer();
		if (!withPermission(p, "convert") || testInEdit(session)) return;
		Location l1 = session.getLocation1(), l2 = session.getLocation2();
		if (l1 != null && l2 != null) {
			
			if (!l1.getWorld().getName().equals(l2.getWorld().getName())) {
				p.sendMessage(getConfigString("must-same-world-points", session));
				return;
			}
			double vol = session.getPermittedVolume();
			if (session.getSelectionVolume() > vol) {
				p.sendMessage(getConfigString("exceeds-max-volume", session).replaceAll("\\Q%volume%\\E", "" + vol));
				return;
			}
			
			ConvertTransaction t = new ConvertTransaction();
			session.setCancelPhysics(true);
			
			DexterityDisplay d = api.convertBlocks(l1, l2, t, (int) vol + 1);
			
			session.setCancelPhysics(false);
			if (session.getSelected() != null && !session.getSelected().isSaved() && session.getSelected().getCenter().getWorld().getName().equals(d.getCenter().getWorld().getName())) session.getSelected().hardMerge(d); //within cuboid selection
			else session.setSelected(d, false);
			
			session.pushTransaction(t);
							
			//p.sendMessage(cc + "Created a new display: " + cc2 + d.getLabel() + cc + "!");
			p.sendMessage(getConfigString("convert-success", session));
			
		} else p.sendMessage(getConfigString("need-locations", session));
	}
	
	public void move(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "move");
		if (d == null) return;
		
		Player p = ct.getPlayer();
		List<String> flags = ct.getFlags();
		
		boolean same_world = d.getCenter().getWorld().getName().equals(p.getWorld().getName());
		if (ct.getArgs().length == 1 || !same_world) {
			if (!same_world) d.teleport(p.getLocation());
			session.startFollowing();
			session.startEdit(d, EditType.TRANSLATE, false, new BlockTransaction(d));
			p.sendMessage(getConfigString("to-finish-edit", session));
			return;
		}
		
		BlockTransaction t = new BlockTransaction(d);
		Location loc;
		if (flags.contains("continuous") || flags.contains("c")) loc = p.getLocation();
		else if (flags.contains("here")) loc = DexUtils.blockLoc(p.getLocation()).add(0.5, 0.5, 0.5);
		else loc = d.getCenter();
					
		HashMap<String,Double> attr_d = ct.getDoubleAttrs();
		if (attr_d.containsKey("x")) loc.add(attr_d.get("x"), 0, 0);
		if (attr_d.containsKey("y")) loc.add(0, attr_d.get("y"), 0);
		if (attr_d.containsKey("z")) loc.add(0, 0, attr_d.get("z"));
		if (attr_d.containsKey("rx") || attr_d.containsKey("ry") || attr_d.containsKey("rz")) {
			DexRotation rot = d.getRotationManager(false);
			Vector x, y, z;
			if (rot == null) {
				x = new Vector(1, 0, 0);
				y = new Vector(0, 1, 0);
				z = new Vector(0, 0, 1);
			} else {
				x = rot.getXAxis();
				y = rot.getYAxis();
				z = rot.getZAxis();
			}
			
			if (attr_d.containsKey("rx")) loc.add(x.multiply(attr_d.get("rx")));
			if (attr_d.containsKey("ry")) loc.add(y.multiply(attr_d.get("ry")));
			if (attr_d.containsKey("rz")) loc.add(z.multiply(attr_d.get("rz")));
		}
					
		d.teleport(loc);
		
		t.commit(d.getBlocks());
		t.commitCenter(d.getCenter());
		session.pushTransaction(t);
		
		if (session.getFollowingOffset() != null) {
			Location loc2 = p.getLocation();
			if (!p.isSneaking()) DexUtils.blockLoc(loc2);
			session.setFollowingOffset(d.getCenter().toVector().subtract(loc2.toVector()));
		}
	}
	
	public void save(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "save", "label");
		if (d == null) return;
		
		String[] args = ct.getArgs();
		Player p = ct.getPlayer();
		
		if (args.length != 2) {
			if (args[0].equals("save") || args[0].equals("label")) p.sendMessage(getUsage("rename").replaceAll(" name", " label"));
			else p.sendMessage(getUsage("rename"));
			return;
		}
		if (d.getBlocksCount() == 0) {
			p.sendMessage(getConfigString("must-select-display", session));
			return;
		}
		if (args[1].startsWith("-") || args[1].contains(".")) {
			p.sendMessage(plugin.getConfigString("invalid-name").replaceAll("\\Q%input%\\E", args[1]));
			return;
		}
		
		if (d.setLabel(args[1])) {
			d.addOwner(p);
			p.sendMessage(getConfigString("rename-success", session));
		}
		else p.sendMessage(getConfigString("name-in-use", session).replaceAll("\\Q%input%\\E", args[1]));
	}
	
	public void unsave(CommandContext ct) {
		DexterityDisplay d;
		String def = ct.getDefaultArg();
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		if (def != null) {
			d = plugin.getDisplay(def.toLowerCase());
			if (d == null) {
				p.sendMessage(getConfigString("display-not-found", session).replaceAll("\\Q%input%\\E", def));
				return;
			}
		} else {
			d = getSelected(session, "save", "label");
			if (d == null) return;
			if (!d.isSaved()) {
				p.sendMessage(getConfigString("not-saved", session));
				return;
			}
		}
		
		String msg = getConfigString("unsave-success", session);
		d.unregister();
		p.sendMessage(msg);
	}
	
	public void undo(CommandContext ct) {
		int count = ct.getIntAttrs().getOrDefault("count", -1);
		if (count < 2) ct.getSession().undo();
		else ct.getSession().undo(count);
	}
	
	public void redo(CommandContext ct) {
		int count = ct.getIntAttrs().getOrDefault("count", -1);
		if (count < 2) ct.getSession().redo();
		else ct.getSession().redo(count);
	}
	
	public void mask(CommandContext ct) {
		String[] args = ct.getArgs();
		List<String> flags = ct.getFlags();
		DexSession session = ct.getSession();
		Player p = ct.getPlayer();
		
		if (args.length < 2 || flags.contains("none") || flags.contains("off") || ct.getDefaultArg().equals("none") || ct.getDefaultArg().equals("off")) {
			session.setMask(null);
			p.sendMessage(getConfigString("mask-success-disable", session));
		} else {
			Mask m = new Mask();
			for (String mat : ct.getDefaultArgs()) {
				try {
					m.addMaterialsList(mat);
				} catch (IllegalArgumentException ex) {
					p.sendMessage(plugin.getConfigString("unknown-material").replaceAll("\\Q%input%\\E", mat));
					return;
				}
			}
			
			if (flags.contains("invert")) m.setNegative(true);

			session.setMask(m);
			
			p.sendMessage(getConfigString("mask-success", session).replaceAll("\\Q%input%\\E", m.toString()));
		}
	}
	
	public void rotate(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "rotate");
		if (d == null) return;
		
		String[] args = ct.getArgs();
		List<String> flags = ct.getFlags();
		Player p = ct.getPlayer();
		
		if (args.length < 2) {
			p.sendMessage(getUsage("rotate"));
			return;
		}
		
		RotationPlan plan = new RotationPlan();
		boolean set = flags.contains("set");
		if (flags.contains("reset")) {
			plan.reset = true;
			set = true;
		}
		HashMap<String, Double> attrs_d = ct.getDoubleAttrs();
		List<String> defs_n = DexUtils.getDefaultAttributesWithFlags(args);
		
		plan.yawDeg = attrs_d.getOrDefault("yaw", Double.MAX_VALUE);
		plan.pitchDeg = attrs_d.getOrDefault("pitch", Double.MAX_VALUE);
		plan.rollDeg = attrs_d.getOrDefault("roll", Double.MAX_VALUE);
		plan.yDeg = attrs_d.getOrDefault("y", Double.MAX_VALUE);
		plan.xDeg = attrs_d.getOrDefault("x", Double.MAX_VALUE);
		plan.zDeg = attrs_d.getOrDefault("z", Double.MAX_VALUE);
		
		try {
			switch(Math.min(ct.getDefaultArgs().size(), 6)) {
			case 6:
				if (plan.zDeg == Double.MAX_VALUE) plan.zDeg = Double.parseDouble(defs_n.get(5));
			case 5:
				if (plan.xDeg == Double.MAX_VALUE) plan.xDeg = Double.parseDouble(defs_n.get(4));
			case 4: 
				if (plan.yawDeg == Double.MAX_VALUE) plan.yawDeg = Double.parseDouble(defs_n.get(3));
			case 3: 
				if (plan.rollDeg == Double.MAX_VALUE) plan.rollDeg = Double.parseDouble(defs_n.get(2));
			case 2: 
				if (plan.pitchDeg == Double.MAX_VALUE) plan.pitchDeg = Double.parseDouble(defs_n.get(1));
			case 1: 
				if (plan.yDeg == Double.MAX_VALUE) plan.yDeg = Double.parseDouble(defs_n.get(0));
			default:
			}
		} catch (Exception ex) {
			p.sendMessage(getConfigString("must-send-number", session));
			return;
		}
		
		if (plan.yawDeg == Double.MAX_VALUE) {
			plan.setYaw = false;
			plan.yawDeg = 0;
		} else plan.setYaw = set;
		if (plan.pitchDeg == Double.MAX_VALUE) {
			plan.setPitch = false;
			plan.pitchDeg = 0;
		} else plan.setPitch = set;
		if (plan.rollDeg == Double.MAX_VALUE) {
			plan.setRoll = false;
			plan.rollDeg = 0;
		} else plan.setRoll = set;
		if (plan.yDeg == Double.MAX_VALUE) {
			plan.setY = false;
			plan.yDeg = 0;
		} else plan.setY = set;
		if (plan.xDeg == Double.MAX_VALUE) {
			plan.setX = false;
			plan.xDeg = 0;
		} else plan.setX = set;
		if (plan.zDeg == Double.MAX_VALUE) {
			plan.setZ = false;
			plan.zDeg = 0;
		} else plan.setZ = set;
								
		RotationTransaction t = new RotationTransaction(d);
		d.getRotationManager(true).setTransaction(t);
		
		if (d.rotate(plan) == null) {
			p.sendMessage(getConfigString("must-send-number", session));
			return;
		}
		
		session.pushTransaction(t); //commit done in async callback
	}
	
	public void schematic(CommandContext ct) {
		Player p = ct.getPlayer();
		if (!withPermission(p, "schematic")) return;
		String[] args = ct.getArgs();
		DexSession session = ct.getSession();

		if (args.length == 1) p.sendMessage(getUsage("schematic"));
		else if (args[1].equalsIgnoreCase("import") || args[1].equalsIgnoreCase("load")) { //d schem import, /d schem load
			if (!withPermission(p, "schem.import") || testInEdit(session)) return;
			
			if (args.length == 2 || ct.getDefaultArgs().size() < 2) {
				p.sendMessage(getUsage("schematic"));
				return;
			}

			String name = ct.getDefaultArgs().get(1);
			if (!api.checkSchematicExists(name)) {
				p.sendMessage(plugin.getConfigString("unknown-input").replaceAll("\\Q%input%\\E", name));
				return;
			}
			
			DexterityDisplay d;
			Schematic schem;
			try {
				schem = new Schematic(plugin, name);
				d = schem.paste(p.getLocation());
				d.addOwner(p);
			} catch (Exception ex) {
				ex.printStackTrace();
				p.sendMessage(plugin.getConfigString("console-exception"));
				return;
			}
			session.setSelected(d, false);
			p.sendMessage(getConfigString("schem-import-success", session).replaceAll("\\Q%author%\\E", schem.getAuthor()));
		}
		else if (args[1].equalsIgnoreCase("export") || args[1].equalsIgnoreCase("save")) { //d schem export, /d schem save
			if (!withPermission(p, "schem.export")) return;
			DexterityDisplay d = getSelected(session, "export");
			if (d == null || testInEdit(session)) return;

			String label;
			if (args.length >= 3) label = args[2];
			else {
				if (!d.isSaved()) {
					p.sendMessage(getUsage("schematic"));
					return;
				}
				label = d.getLabel();
			}
			label = label.toLowerCase();
			
			SchematicBuilder builder = new SchematicBuilder(plugin, d);
			String author = p.getName();
			HashMap<String, String> attr_str = ct.getStringAttrs();
			if (attr_str.containsKey("author")) author = attr_str.get("author");
			boolean overwrite = ct.getFlags().contains("overwrite");
			
			int res = builder.save(label, author, overwrite);
			
			if (res == 0) p.sendMessage(getConfigString("schem-export-success", session));
			else if (res == 1) p.sendMessage(getConfigString("file-already-exists", session).replaceAll("\\Q%input%\\E", "/schematics/" + label + ".dexterity"));
			else if (res == -1) p.sendMessage(getConfigString("console-exception", session));
		}
		else if (args[1].equalsIgnoreCase("delete")) { //d schem delete
			if (args.length <= 2) {
				p.sendMessage(getUsage("schematic"));
				return;
			}
			String name = args[2].toLowerCase();
			if (!name.endsWith(".dexterity")) name += ".dexterity";
			File f = new File(plugin.getDataFolder().getAbsolutePath() + "/schematics/" + name);
			if (f.exists()) {
				try {
					f.delete();
					p.sendMessage(plugin.getConfigString("schem-delete-success").replaceAll("\\Q%label%\\E", args[2].toLowerCase()).replaceAll("\\Q%input%\\E", name));
				} catch (Exception ex) {
					ex.printStackTrace();
					p.sendMessage(plugin.getConfigString("file-not-found").replaceAll("\\Q%input%\\E", name));
				}
			} else p.sendMessage(plugin.getConfigString("file-not-found").replaceAll("\\Q%input%\\E", name));
		}
		else if (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("lsit")) { //d schem list
			int page = 0;
			HashMap<String, Integer> attrs = ct.getIntAttrs();
			if (attrs.containsKey("page")) page = Math.max(attrs.get("page") - 1, 0);
			else if (args.length >= 3) page = Math.max(DexUtils.parseInt(args[2]) - 1, 0);
			List<String> schems = listSchematics();
			
			if (schems.size() == 0) {
				p.sendMessage(plugin.getConfigString("list-empty"));
				return;
			}
			
			String[] formatted = new String[schems.size()];
			for (int i = 0; i < schems.size(); i++) formatted[i] = cc + "- " + cc2 + schems.get(i);
			
			int maxpage = DexUtils.maxPage(schems.size(), 5);
			if (page >= maxpage) page = maxpage - 1;
			p.sendMessage(plugin.getConfigString("schem-list-header").replaceAll("\\Q%page%\\E", "" + (page+1)).replaceAll("\\Q%maxpage%\\E", "" + maxpage));
			DexUtils.paginate(p, formatted, page, 5);
		}
		else p.sendMessage(plugin.getConfigString("unknown-subcommand"));
	}
	
	public void info(CommandContext ct) {
		DexterityDisplay d = getSelected(ct.getSession());
		if (d == null) return;
		if (ct.getFlags().contains("scale")) {
			Vector scale = d.getScale();
			int decimals = 6;
			String msg = getConfigString("info-format-scale", ct.getSession())
					.replaceAll("\\Q%x%\\E", "" + DexUtils.round(scale.getX(), decimals))
					.replaceAll("\\Q%y%\\E", "" + DexUtils.round(scale.getY(), decimals))
					.replaceAll("\\Q%z%\\E", DexUtils.round(scale.getZ(), decimals));
			ct.getPlayer().sendMessage(msg);
		} else {
			String msg = plugin.getConfigString(d.getLabel() == null ? "info-format" : "info-format-saved")
					.replaceAll("\\Q%count%\\E", "" + d.getBlocksCount())
					.replaceAll("\\Q%world%\\E", d.getCenter().getWorld().getName());
			if (d.getLabel() != null) msg = msg.replaceAll("\\Q%label%\\E", d.getLabel());

			ct.getPlayer().sendMessage(msg);
		}
		api.markerPoint(d.getCenter(), Color.AQUA, 4);
	}
	
	public void remove(CommandContext ct) {
		DexSession session = ct.getSession();
		Player p = ct.getPlayer();
		boolean res = !(ct.getArgs()[0].equals("remove") || ct.getArgs()[0].equals("rm"));
		if ((res && !withPermission(p, "remove")) || (!res && !withPermission(p, "deconvert"))) return;
		
		if (testInEdit(session)) return;
		
		String def = ct.getDefaultArg();
		DexterityDisplay d;
		if (def == null) {
			d = session.getSelected();
			if (d == null) {
				ct.getPlayer().sendMessage(plugin.getConfigString("must-select-display"));
				return;
			}
		}
		else {
			d = plugin.getDisplay(def);
			if (d == null) {
				p.sendMessage(plugin.getConfigString("display-not-found").replaceAll("\\Q%input%\\E", def));
				return;
			}
			
		}
		
		api.unTempHighlight(d);
		
		Transaction t;
		if (res) {
			t = new DeconvertTransaction(d);
			session.setCancelPhysics(true);
		}
		else t = new RemoveTransaction(d);
		
		String label = d.getLabel();
		d.remove(res);
		
		if (res) {
			p.sendMessage(getConfigString("restore-success", session, label));
			session.setCancelPhysics(false);
		}
		else p.sendMessage(getConfigString("remove-success", session, label));
		
		session.setSelected(null, false);
		session.pushTransaction(t);
	}
	
	public void list(CommandContext ct) {
		Player p = ct.getPlayer();
		if (!withPermission(p, "list")) return;
		
		HashMap<String, Integer> intAttrs = ct.getIntAttrs();
		HashMap<String, String> strAtrs = ct.getStringAttrs();
		
		int page = 0;
		if (intAttrs.containsKey("page")) {
			page = Math.max(intAttrs.get("page") - 1, 0);
		} else if (ct.getArgs().length >= 2) page = Math.max(DexUtils.parseInt(ct.getArgs()[1]) - 1, 0);			
		int maxpage = DexUtils.maxPage(plugin.getDisplays().size(), 10);
		if (page >= maxpage) page = maxpage - 1;
		
		String worldNameFilter = null;
		if (strAtrs.containsKey("world")) {
			World world = Bukkit.getWorld(strAtrs.get("world"));
			if (world != null) worldNameFilter = world.getName();
		}
		boolean all = ct.getFlags().contains("all");
		
		int total = 0;
		List<DexterityDisplay> filteredDisplays = new ArrayList<>();
		for (DexterityDisplay d : plugin.getDisplays()) {
			if (d.getLabel() == null || (worldNameFilter != null && !d.getCenter().getWorld().getName().equals(worldNameFilter))) continue;
			if (!all && !d.isListed()) continue; //temporary display
			total += d.getGroupSize();
			filteredDisplays.add(d);
		}
		
		if (total == 0) {
			p.sendMessage(plugin.getConfigString("no-saved-displays"));
			return;
		}
		
		p.sendMessage(plugin.getConfigString("list-page-header").replaceAll("\\Q%page%\\E", "" + (page+1)).replaceAll("\\Q%maxpage%\\E", ""+maxpage));
		String[] strs = new String[total];
		
		int i = 0;
		for (DexterityDisplay disp : filteredDisplays) {
			DexSession session = ct.getSession();
			i += constructList(strs, disp, session.getSelected() == null ? null : session.getSelected().getLabel(), i, 0);
		}
		
		DexUtils.paginate(p, strs, page, 10);
	}
	
	public void scale(CommandContext ct) {
		DexSession session = ct.getSession();
		DexterityDisplay d = getSelected(session, "scale");
		if (d == null) return;
		
		boolean set = ct.getFlags().contains("set");
		HashMap<String, Double> attrsd = ct.getDoubleAttrs();
		Player p = ct.getPlayer();
		
		Vector scale = new Vector();
		String scale_str;
		if (attrsd.containsKey("x") || attrsd.containsKey("y") || attrsd.containsKey("z")) {
			float sx = Math.abs(attrsd.getOrDefault("x", set ? d.getScale().getX() : 1).floatValue());
			float sy = Math.abs(attrsd.getOrDefault("y", set ? d.getScale().getY() : 1).floatValue());
			float sz = Math.abs(attrsd.getOrDefault("z", set ? d.getScale().getZ() : 1).floatValue());
			
			if (sx == 0 || sy == 0 || sz == 0) {
				p.sendMessage(getConfigString("must-send-number", session));
				return;
			}
			
			scale_str = sx + ", " + sy + ", " + sz;
			scale = new Vector(sx, sy, sz);
		} else {
			float scalar;
			try {
				scalar = Float.parseFloat(ct.getDefaultArg());
			} catch(Exception ex) {
				p.sendMessage(getConfigString("must-send-number", session));
				return;
			}
			scale_str = "" + scalar;
			scale = new Vector(scalar, scalar, scalar);
		}
		
		ScaleTransaction t = new ScaleTransaction(d);
		try {
			Vector curr_scale = d.getScale();
			Vector new_scale = set ? 
					new Vector(scale.getX() / curr_scale.getX(), scale.getY() / curr_scale.getY(), scale.getZ() / curr_scale.getZ()) 
					: scale.clone();
			
			double max_scale = plugin.getConfig().getDouble("max-scale"), min_scale = plugin.getConfig().getDouble("min-scale");
			if (max_scale > 1 || min_scale > 0) {
				for (DexBlock db : d.getBlocks()) {
					Vector db_scale = DexUtils.hadimard(new_scale, db.getTransformation().getScale());
					if (max_scale > 1 && DexUtils.max(db_scale) > max_scale) {
						p.sendMessage(getConfigString("cannot-exceed-scale-limit", session));
						return;
					}
					if (min_scale > 0 && DexUtils.min(new_scale) < min_scale) {
						p.sendMessage(getConfigString("cannot-exceed-scale-limit", session));
						return;
					}
				}
			}
			
			if (set) {
				d.setScale(scale);
				p.sendMessage(getConfigString("scale-success-set", session).replaceAll("\\Q%scale%\\E", scale_str));
			}
			else {
				d.scale(scale);
				p.sendMessage(getConfigString("scale-success", session).replaceAll("\\Q%scale%\\E", scale_str));
			}
		} catch (DexterityException ex) {
			p.sendMessage(getConfigString("selection-too-complex", session).replaceAll("\\Q%scale%\\E", scale_str));
			return;
		}

		t.commit();
		session.pushTransaction(t);
	}
	
	public void item(CommandContext ct) {
		Player p = ct.getPlayer();
		if (!p.hasPermission("dexterity.command.item")) {
			p.sendMessage(noperm);
			return;
		}
		DexSession session = ct.getSession();
		ItemStack item = p.getInventory().getItemInMainHand();
		if (item == null || item.getType() == Material.AIR || item.getType() == Material.WOODEN_AXE || item.getType() == plugin.getWandType()) {
			p.sendMessage(getConfigString("must-hold-item", session));
			return;
		}
		
		NamespacedKey key = new NamespacedKey(plugin, "dex-schem-label");
		ItemMeta meta = item.getItemMeta();
		String schem_name = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if (schem_name != null) {
			for (String label : plugin.getDisplayLabels()) {
				if (label.startsWith(schem_name)) {
					p.sendMessage(plugin.getConfigString("cannot-delete-item-schem"));
					return;
				}
			}
			
			meta.setDisplayName(null);
			meta.getPersistentDataContainer().remove(key);
			item.setItemMeta(meta);
			
			File f = new File(plugin.getDataFolder().getAbsolutePath() + "/schematics/" + schem_name + ".dexterity");
			if (f.exists()) {
				try {
					f.delete();
				} catch (Exception ex) {}
			}
			
			p.sendMessage(getConfigString("item-success-remove", session));
			return;
		}
		
		DexterityDisplay d = getSelected(session, "item");
		if (d == null) return;
		if (!d.isSaved()) {
			p.sendMessage(getConfigString("not-saved", session));
			return;
		}
		
		item = item.clone();
		item.setAmount(1);

		Random random = new Random();
		String schem_label = DexUtils.getEffectiveLabel(d.getLabel()) + "-temp-" + Math.abs(random.nextInt()); 
		SchematicBuilder schem= new SchematicBuilder(plugin, d);
		schem.save(schem_label, p.getName(), true);

		d.setDropItem(item, schem_label);
		if (ct.getPlayer().getLocation().distance(d.getCenter()) < 10) {
			d.dropNaturally();
			session.setSelected(null, false);
		}
		p.getInventory().removeItem(item);
		p.sendMessage(getConfigString("item-success", session));
	}
	
	public void merge(CommandContext ct) {
		Player p = ct.getPlayer();
		DexSession session = ct.getSession();
		String def = ct.getDefaultArg();
		if (def == null) {
			p.sendMessage(getUsage("merge"));
			return;
		}
		DexterityDisplay d = getSelected(session, "merge");
		if (d == null || testInEdit(session)) return;
		DexterityDisplay parent = plugin.getDisplay(def);
		if (parent == null) {
			p.sendMessage(plugin.getConfigString("display-not-found").replaceAll("\\Q%input%\\E", def));
			return;
		}
		HashMap<String, String> attr_str = ct.getStringAttrs();
		String new_group = attr_str.get("new_group");
		if (d == parent || d.equals(parent)) {
			p.sendMessage(getConfigString("must-be-different", session));
			return;
		}
		if (!d.getCenter().getWorld().getName().equals(parent.getCenter().getWorld().getName())) {
			p.sendMessage(getConfigString("must-same-world", session));
			return;
		}
		if (d.getParent() != null) {
			p.sendMessage(getConfigString("cannot-merge-subgroups", session));
			return;
		}
		if (d.containsSubdisplay(parent)) {
			p.sendMessage(getConfigString("already-merged", session));
			return;
		}
		if (new_group != null && plugin.getDisplayLabels().contains(new_group)) {
			p.sendMessage(getConfigString("group-name-in-use", session));
			return;
		}
		
		boolean hard = true; //flags.contains("hard");
		if (hard) {
			if (!d.canHardMerge() || !parent.canHardMerge()) {
				p.sendMessage(getConfigString("cannot-hard-merge", session));
				return;
			}
			
			if (parent.hardMerge(d)) {
				session.setSelected(parent, false);
				p.sendMessage(getConfigString("merge-success-hard", session));
			}
			else p.sendMessage(getConfigString("failed-merge", session));
		} else {
			DexterityDisplay g = d.merge(parent, new_group);
			if (g != null) {
				session.setSelected(g, false);
				if (new_group == null) p.sendMessage(getConfigString("merge-success", session).replaceAll("\\Q%parentlabel%\\E", parent.getLabel()));
				else p.sendMessage(getConfigString("merge-success-newgroup", session).replaceAll("\\Q%input%\\E", new_group));
			} else p.sendMessage(getConfigString("failed-merge", session));
		}
	}
	
	
}
