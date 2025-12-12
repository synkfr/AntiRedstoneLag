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
    @SuppressWarnings("unused") // Reserved for future use
    private final MessageManager messageManager;
    @SuppressWarnings("unused") // Reserved for future use
    private final LogManager logManager;

    // Track who placed redstone blocks (block key -> player UUID)
    private final Map<String, UUID> blockOwners = new ConcurrentHashMap<>();

    // Cached config values for performance
    private volatile Set<Material> cachedRedstoneMaterials;
    private volatile Set<String> cachedEnabledWorlds;
    private volatile ConfigManager.RemovalAction cachedRemovalAction;

    public RedstoneListener(CounterManager counterManager, ConfigManager configManager,
                            MessageManager messageManager, LogManager logManager) {
        this.counterManager = counterManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
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
            String blockKey = getBlockKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
            blockOwners.put(blockKey, event.getPlayer().getUniqueId());
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

        // Check bypass permission for block owner
        if (hasOwnerBypass(blockKey)) {
            return;
        }

        Chunk chunk = block.getChunk();

        // Use chunk key directly to avoid Location object creation
        String chunkKey = getChunkKey(world, chunk.getX(), chunk.getZ());

        // Check whitelist mode - skip if chunk is not whitelisted
        if (!configManager.isChunkWhitelisted(chunkKey)) {
            return;
        }

        counterManager.incrementCounters(chunkKey, blockKey);

        // Check if we should warn the player (approaching threshold)
        if (counterManager.shouldWarn(chunkKey, blockKey)) {
            UUID ownerUuid = blockOwners.get(blockKey);
            if (ownerUuid != null) {
                Location location = block.getLocation();
                counterManager.sendWarning(location, material, ownerUuid);
            }
        }

        if (counterManager.shouldDisable(chunkKey, blockKey)) {
            // Apply configured removal action
            applyRemovalAction(block, material, event);

            // Clean up owner tracking
            blockOwners.remove(blockKey);

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

    private boolean hasOwnerBypass(String blockKey) {
        UUID ownerUuid = blockOwners.get(blockKey);

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

    // Clean up tracking for unloaded chunks
    public void cleanupChunk(Chunk chunk) {
        String prefix = chunk.getWorld().getName() + ":";
        blockOwners.keySet().removeIf(key -> {
            if (!key.startsWith(prefix)) return false;
            String[] parts = key.split(":");
            if (parts.length < 4) return false;
            int x = Integer.parseInt(parts[1]) >> 4;
            int z = Integer.parseInt(parts[3]) >> 4;
            return x == chunk.getX() && z == chunk.getZ();
        });
    }
}