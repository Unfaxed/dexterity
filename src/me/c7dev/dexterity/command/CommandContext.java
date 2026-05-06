package me.c7dev.dexterity.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

import me.c7dev.dexterity.DexSession;
import me.c7dev.dexterity.Dexterity;
import me.c7dev.dexterity.util.DexUtils;

/**
 * Context required for any in-game /dex command
 */
public class CommandContext {

	private final Player p;
	private final String[] args;
	private final Dexterity plugin;
	private final DexSession session;
	
	private HashMap<String,Integer> attr;
	private HashMap<String,String> attrStr;
	private HashMap<String, Double> attrDoubles;
	private List<String> defs;
	private Set<String> flags;
	
	public CommandContext(Dexterity plugin, Player p, String[] args) {
		if (plugin == null) throw new IllegalArgumentException("Plugin instance cannot be null!");
		if (p == null) throw new IllegalArgumentException("Player cannot be null!");
		if (args == null) throw new IllegalArgumentException("Arguments cannot be null!");
		
		this.p = p;
		this.args = args;
		this.plugin = plugin;
		this.session = plugin.getEditSession(p.getUniqueId());
	}
	
	public HashMap<String,Integer> getIntAttrs() {
		if (attr == null) {
			attr = new HashMap<>();
			for (String arg : args) {
				String[] argsplit = arg.split("[=,:]");
				if (argsplit.length > 0) {
					attr.put(DexUtils.attrAlias(argsplit[0]), DexUtils.valueAlias(argsplit[argsplit.length-1]));
				}
			}
		}
		return attr;
	}
	
	public HashMap<String, String> getStringAttrs(){
		if (attrStr == null) {
			attrStr = new HashMap<>();
			for (String arg : args) {
				String[] argsplit = arg.toLowerCase().split("[=,:]");
				if (argsplit.length > 0) {
					attrStr.put(DexUtils.attrAlias(argsplit[0]), argsplit[argsplit.length-1]);
				}
			}
		}
		return attrStr;
	}
	
	public HashMap<String, Double> getDoubleAttrs(){
		if (attrDoubles == null) {
			attrDoubles = new HashMap<>();
			for (String arg : args) {
				String[] argsplit = arg.split("[=,:]");
				if (argsplit.length > 0) {
					String alias = DexUtils.attrAlias(argsplit[0]);
					try {
						double d = Double.parseDouble(argsplit[argsplit.length-1]);
						if (argsplit[0].equalsIgnoreCase("down") || argsplit[0].equalsIgnoreCase("west") || argsplit[0].equalsIgnoreCase("north")) d*=-1;
						attrDoubles.put(alias, d);
					} catch (Exception ex) {
						try {
							attrDoubles.put(alias, (double) DexUtils.valueAlias(argsplit[argsplit.length-1]));
						} catch (Exception ex2) {
							
						}
					}
				}
			}
		}
		return attrDoubles;
	}
	
	public Set<String> getFlags(){
		if (flags == null) {
			flags = new HashSet<String>();
			for (String arg : args) {
				if (arg.startsWith("-")) flags.add(arg.toLowerCase().replaceFirst("-", ""));
			}
		}
		return flags;
	}
	
	public List<String> getDefaultArgs(){
		if (defs == null) {
			defs = new ArrayList<>();
			for (int i = 1; i < args.length; i++) {
				String arg = args[i];
				if (!arg.contains("=") && !arg.contains(":") && !arg.startsWith("-")) {
					defs.add(arg.toLowerCase());
				}
			}
		}
		return defs;
	}
	
	public String getDefaultArg() {
		List<String> ldefs = getDefaultArgs();
		return ldefs.size() > 0 ? ldefs.get(0) : null;
	}
	
	public Player getPlayer() {
		return p;
	}
	
	public String[] getArgs() {
		return args;
	}
	
	public Dexterity getPlugin() {
		return plugin;
	}
	
	public DexSession getSession() {
		return session;
	}

}
