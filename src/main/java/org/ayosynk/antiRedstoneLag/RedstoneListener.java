package org.ayosynk.antiRedstoneLag;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

public class RedstoneListener implements Listener {
    private final CounterManager counterManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;

    public RedstoneListener(CounterManager counterManager, ConfigManager configManager,
                            MessageManager messageManager, LogManager logManager) {
        this.counterManager = counterManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();

        // Check if world is enabled
        if (!configManager.isWorldEnabled(block.getWorld().getName())) {
            return;
        }

        // Check if material is monitored
        if (!configManager.getRedstoneMaterials().contains(material)) {
            return;
        }

        Chunk chunk = block.getChunk();
        Location location = block.getLocation();

        counterManager.incrementCounters(chunk, location);

        if (counterManager.shouldDisable(chunk, location)) {
            // Store material name before removing
            String materialName = material.toString();
            block.setType(Material.AIR, false);

            // Handle removal (logging, alerts, etc.)
            counterManager.handleRedstoneRemoval(location, material);
        }
    }
}