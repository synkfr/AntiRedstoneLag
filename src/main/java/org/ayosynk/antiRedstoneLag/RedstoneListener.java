package org.ayosynk.antiRedstoneLag;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for redstone events and block placements.
 * Monitors redstone activity and applies configured actions when thresholds are exceeded.
 */
public class RedstoneListener implements Listener {
    private static final String BYPASS_PERMISSION = "antiredstonelag.bypass";

    private final CounterManager counterManager;
    private final ConfigManager configManager;

    // Track who placed redstone blocks (Chunk key -> Block key -> player UUID)
    private final Map<String, Map<String, UUID>> blockOwners = new ConcurrentHashMap<>();

    // Cached config values for performance
    private volatile Set<Material> cachedRedstoneMaterials;
    private volatile Set<String> cachedEnabledWorlds;
    private volatile ConfigManager.RemovalAction cachedRemovalAction;

    public RedstoneListener(CounterManager counterManager, ConfigManager configManager) {
        this.counterManager = counterManager;
        this.configManager = configManager;
        refreshCache();
    }

    /**
     * Refresh cached config values. Call this after config reload.
     */
    public void refreshCache() {
        this.cachedRedstoneMaterials = configManager.getRedstoneMaterials();
        this.cachedEnabledWorlds = configManager.getEnabledWorlds();
        this.cachedRemovalAction = configManager.getRemovalAction();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();

        // Track redstone block placements using cached materials
        if (cachedRedstoneMaterials.contains(material)) {
            // Avoid creating Location object - use block coordinates directly
            String chunkKey = getChunkKey(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
            String blockKey = getBlockKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
            
            blockOwners.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>())
                       .put(blockKey, event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        World world = block.getWorld();
        String worldName = world.getName();

        // Check if world is enabled using cached worlds
        if (!cachedEnabledWorlds.contains("*") && !cachedEnabledWorlds.contains(worldName)) {
            return;
        }

        // Check if material is monitored using cached materials
        if (!cachedRedstoneMaterials.contains(material)) {
            return;
        }

        // Get block coordinates once to avoid repeated calls
        int blockX = block.getX();
        int blockY = block.getY();
        int blockZ = block.getZ();
        String blockKey = getBlockKey(world, blockX, blockY, blockZ);

        // Use chunk key directly to avoid Location object creation
        String chunkKey = getChunkKey(world, blockX >> 4, blockZ >> 4);

        // Check bypass permission for block owner
        if (hasOwnerBypass(chunkKey, blockKey)) {
            return;
        }

        // Check whitelist mode - skip if chunk is not whitelisted
        if (!configManager.isChunkWhitelisted(chunkKey)) {
            return;
        }

        counterManager.incrementCounters(chunkKey, blockKey);

        // Check if we should warn the player (approaching threshold)
        if (counterManager.shouldWarn(chunkKey, blockKey)) {
            Map<String, UUID> chunkOwners = blockOwners.get(chunkKey);
            UUID ownerUuid = chunkOwners != null ? chunkOwners.get(blockKey) : null;
            if (ownerUuid != null) {
                Location location = block.getLocation();
                counterManager.sendWarning(location, material, ownerUuid);
            }
        }

        if (counterManager.shouldDisable(chunkKey, blockKey)) {
            // Apply configured removal action
            applyRemovalAction(block, material, event);

            // Clean up owner tracking
            Map<String, UUID> chunkOwners = blockOwners.get(chunkKey);
            if (chunkOwners != null) {
                chunkOwners.remove(blockKey);
                if (chunkOwners.isEmpty()) {
                    blockOwners.remove(chunkKey);
                }
            }

            // Handle removal (logging, alerts, etc.) - create Location only when needed
            Location location = block.getLocation();
            counterManager.handleRedstoneRemoval(location, material);
        }
    }

    private void applyRemovalAction(Block block, Material material, BlockRedstoneEvent event) {
        switch (cachedRemovalAction) {
            case REMOVE:
                block.setType(Material.AIR, false);
                break;
            case DISABLE:
                // Cancel the redstone signal by setting current to 0
                event.setNewCurrent(0);
                break;
            case DROP:
                // Break block and drop item
                block.breakNaturally(new ItemStack(Material.AIR));
                break;
        }
    }

    private boolean hasOwnerBypass(String chunkKey, String blockKey) {
        Map<String, UUID> chunkOwners = blockOwners.get(chunkKey);
        UUID ownerUuid = chunkOwners != null ? chunkOwners.get(blockKey) : null;

        if (ownerUuid != null) {
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null && owner.hasPermission(BYPASS_PERMISSION)) {
                return true;
            }
        }
        return false;
    }

    private String getBlockKey(World world, int x, int y, int z) {
        return world.getName() + ":" + x + ":" + y + ":" + z;
    }

    private String getChunkKey(World world, int chunkX, int chunkZ) {
        return world.getName() + ":" + chunkX + ":" + chunkZ;
    }

    // Clean up tracking for unloaded chunks - O(1) removal
    public void cleanupChunk(Chunk chunk) {
        String chunkKey = getChunkKey(chunk.getWorld(), chunk.getX(), chunk.getZ());
        blockOwners.remove(chunkKey);
    }
}