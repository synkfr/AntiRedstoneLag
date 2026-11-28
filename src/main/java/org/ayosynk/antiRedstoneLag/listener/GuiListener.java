package org.ayosynk.antiRedstoneLag.listener;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.gui.ArlMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Protects Staff GUI inventories from item tampering and dispatches slot callbacks safely.
 */
public class GuiListener implements Listener {
    private final AntiRedstoneLag plugin;

    public GuiListener(AntiRedstoneLag plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ArlMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        plugin.getScheduler().runTask(player, () -> holder.handleClick(slot, event));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ArlMenuHolder) {
            event.setCancelled(true);
        }
    }
}
