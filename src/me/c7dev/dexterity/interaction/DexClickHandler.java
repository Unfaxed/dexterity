package me.c7dev.dexterity.interaction;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import me.c7dev.dexterity.DexSession;
import me.c7dev.dexterity.Dexterity;
import me.c7dev.dexterity.api.events.PlayerBreakBlockDisplayEvent;
import me.c7dev.dexterity.api.events.PlayerClickBlockDisplayEvent;
import me.c7dev.dexterity.api.events.PlayerPlaceBlockDisplayEvent;
import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.displays.animation.Animation;
import me.c7dev.dexterity.displays.animation.RideableAnimation;
import me.c7dev.dexterity.util.ClickedBlock;
import me.c7dev.dexterity.util.ClickedBlockDisplay;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.InteractionCommand;

public class DexClickHandler {
	
	private static final String DEFAULT_WAND_TITLE = "§fDexterity Wand";
	
	private Dexterity plugin;
	private final PlayerInteractEvent event;
	private ClickedBlockDisplay clicked;
	private DexterityDisplay clickedDisplay;
	private boolean rightClick = false, hasClickedDexBlock = false, isHoldingWand = false;
	private final boolean hasBuildPerm, hasClickPerm;
	private DexSession session;
	private DexBlock clickedDB;
	private ItemStack hand;
	
	public DexClickHandler(Dexterity plugin, PlayerInteractEvent event) {
		this.event = event;
		this.plugin = plugin;
		
		hasClickPerm = event.getPlayer().hasPermission("dexterity.click");
		hasBuildPerm = event.getPlayer().hasPermission("dexterity.build");
		if (!hasClickPerm && !hasBuildPerm) return;
		
		initializeClickData();
		if (callClickBlockEvent()) executeAction();
	}
	
	private void initializeClickData() {
		rightClick = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;

		//calculate if player clicked a block display
		hand = event.getPlayer().getInventory().getItemInMainHand();
		if (!rightClick || isHoldingAllowedMaterial()) clicked = plugin.api().getLookingAt(event.getPlayer());

		ClickedBlock clickedBlockData = plugin.api().getPhysicalBlockLookingAtRaw(event.getPlayer(), 0.1, clicked == null ? 5 : clicked.getDistance());
		if (clickedBlockData != null && clickedBlockData.getBlock().getType() == Material.AIR) clickedBlockData = null;
		
		hasClickedDexBlock = rightClick;
		session = plugin.getEditSession(event.getPlayer().getUniqueId());
		isHoldingWand = isPlayerHoldingWand();
		
		if (clicked != null) {
			hasClickedDexBlock = clickedBlockData != null && clickedBlockData.getBlock().getLocation().distance(event.getPlayer().getEyeLocation()) < clicked.getDistance();
			if (clicked.getBlockDisplay().getMetadata("dex-ignore").size() > 0) return;

			clickedDB = plugin.getMappedDisplay(clicked.getBlockDisplay().getUniqueId());
			if (clickedDB != null) clickedDisplay = clickedDB.getDexterityDisplay();
		}
	}
	
	private void executeAction() {
		//normal player or saved display click
		if (hasPlayerClickedSavedDisplayOrNonBuilder()) {
			handleSavedDisplayInteractions();
			return;
		}
		
		if (!hasBuildPerm) return;
		
		if (isHoldingWand) handleWandClick();
		else handleBlockDisplayBreakPlace();
	}
	
	
	private boolean callClickBlockEvent() {
		PlayerClickBlockDisplayEvent clickEvent = new PlayerClickBlockDisplayEvent(event.getPlayer(), clicked, event.getAction(), clickedDisplay);
		Bukkit.getPluginManager().callEvent(clickEvent);
		return !clickEvent.isCancelled();
	}
	
	public boolean hasPlayerClickedSavedDisplayOrNonBuilder() {
		return clickedDisplay != null && clickedDisplay.isSaved() && (!isHoldingWand || !hasBuildPerm);
	}
	
	public boolean isPlayerHoldingWand() {
		return hand.getType() == Material.WOODEN_AXE || (hand.getType() == plugin.getWandType() && hand.getItemMeta().getDisplayName().equals(plugin.getConfigString("wand-title", DEFAULT_WAND_TITLE)));
	}
	
	public boolean isHoldingAllowedMaterial() {
		return DexUtils.isAllowedMaterial(hand.getType()) || hand.getType() == Material.AIR;
	}
	
	private void handleSavedDisplayInteractions() {
		if (clicked == null || clickedDisplay == null) return;
		//click a display as normal player or with nothing in hand
		RideableAnimation ride = (RideableAnimation) clickedDisplay.getAnimation(RideableAnimation.class);
		event.setCancelled(true);

		//drop display item
		if (clickedDisplay.getDropItem() != null && !rightClick && clickedDisplay.hasOwner(event.getPlayer())) {
			if (event.getPlayer().getGameMode() == GameMode.CREATIVE && event.getPlayer().getInventory().containsAtLeast(clickedDisplay.getDropItem(), 1)) {
				clickedDisplay.remove();
			}
			else clickedDisplay.dropNaturally();
			BlockData bdata = Bukkit.createBlockData(clickedDisplay.getDropItem().getType());
			event.getPlayer().playSound(clickedDisplay.getCenter(), bdata.getSoundGroup().getBreakSound(), 1f, 1f);
		}
		//seat or ride
		else if (ride != null && ride.getMountedPlayer() == null) {
			ride.mount(event.getPlayer());
			Animation anim = (Animation) ride;
			anim.start();
		}

		InteractionCommand[] cmds = clickedDisplay.getCommands();
		if (cmds.length == 0) {
			if ((event.getPlayer().hasPermission("dexterity.buid") || event.getPlayer().hasPermission("dexterity.command.cmd"))
					&& clickedDisplay.hasOwner(event.getPlayer()) && clickedDisplay.getDropItem() == null) {
				session.clickMsg();
			}
		} else for (InteractionCommand cmd : cmds) cmd.exec(event.getPlayer(), rightClick);
	}
	
	private void handleWandClick() {
		event.setCancelled(true);

		//select display with wand
		if (!hasClickedDexBlock && clickedDisplay != null && clickedDisplay.getLabel() != null) {
			session.setSelected(clickedDisplay, true);
			return;
		}

		boolean msg = hand.getType() != Material.WOODEN_AXE || event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_AIR;
		if (clicked != null && !hasClickedDexBlock) { //click block with wand (set pos1 or pos2)
			boolean is_l1 = !rightClick;
			Vector scale = DexUtils.hadimard(DexUtils.vector(clicked.getBlockDisplay().getTransformation().getScale()), DexUtils.getBlockDimensions(clicked.getBlockDisplay().getBlock()));
			session.setContinuousLocation(clicked.getDisplayCenterLocation(), is_l1, scale, msg);
		} else if (event.getClickedBlock() != null) {
			if (event.getAction() == Action.LEFT_CLICK_BLOCK) session.setLocation(event.getClickedBlock().getLocation(), true, msg); //pos1
			else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) session.setLocation(event.getClickedBlock().getLocation(), false, msg); //pos2
		}
	}
	
	private void handleBlockDisplayBreakPlace() {
		if (!hasBuildPerm || clicked == null || hasClickedDexBlock) return;
		event.setCancelled(true);

		if (clickedDisplay != null && !clickedDisplay.hasOwner(event.getPlayer())) return;

		//place a block display
		if (rightClick) handleBlockDisplayPlace();
		else handleBlockDisplayBreak();
	}
	
	private void handleBlockDisplayPlace() {
		if (hand.getType() == Material.AIR) return;

		BlockData bdata;
		switch(hand.getType()) { //special case items
		case NETHER_STAR:
			bdata = Bukkit.createBlockData(Material.NETHER_PORTAL);
			break;
		case FLINT_AND_STEEL:
			bdata = Bukkit.createBlockData(Material.FIRE);
			break;
		default:
			if (hand.getType() == clicked.getBlockDisplay().getBlock().getMaterial()) bdata = clicked.getBlockDisplay().getBlock();
			else {
				try {
					bdata = Bukkit.createBlockData(hand.getType());
				} catch (Exception ex) {
					return;
				}
			}
		}

		//call event
		PlayerPlaceBlockDisplayEvent placeEvent = new PlayerPlaceBlockDisplayEvent(event.getPlayer(), clicked, bdata);
		Bukkit.getPluginManager().callEvent(placeEvent);
		if (placeEvent.isCancelled()) return;

		BlockDisplay placed = plugin.putBlock(clicked, bdata);

		if (placed != null) {
			event.getPlayer().playSound(placed.getLocation(), bdata.getSoundGroup().getPlaceSound(), 1f, 1f);

			DexBlock new_db = plugin.getMappedDisplay(placed.getUniqueId());
			if (clickedDisplay != null && session != null && new_db != null) session.pushBlock(new_db, true);
		}
	}
	
	private void handleBlockDisplayBreak() {
		
		//call event
		PlayerBreakBlockDisplayEvent breakEvent = new PlayerBreakBlockDisplayEvent(event.getPlayer(), clicked);
		Bukkit.getPluginManager().callEvent(breakEvent);
		if (breakEvent.isCancelled()) return;
		
		event.getPlayer().playSound(clicked.getBlockDisplay().getLocation(), clicked.getBlockDisplay().getBlock().getSoundGroup().getBreakSound(), 1f, 1f);

		if (clickedDB == null) clicked.getBlockDisplay().remove();
		else {
			if (session != null) session.pushBlock(clickedDB, false);
			clickedDB.remove();
		}
	}
	
}
