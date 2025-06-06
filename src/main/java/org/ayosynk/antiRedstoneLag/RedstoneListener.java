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

    public RedstoneListener(CounterManager counterManager, ConfigManager configManager) {
        this.counterManager = counterManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();

        if (!configManager.getRedstoneMaterials().contains(material)) {
            return;
        }

        Chunk chunk = block.getChunk();
        Location location = block.getLocation();

        counterManager.incrementCounters(chunk, location);

        if (counterManager.shouldDisable(chunk, location)) {
            block.setType(Material.AIR, false);
            // Optional: Log action
        }
    }
}