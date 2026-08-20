package org.ayosynk.antiRedstoneLag.listener;

import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedstoneListener implements Listener {
    private static final String BYPASS_PERMISSION = "antiredstonelag.bypass";

    /**
     * Cached once at class-load time.
     * Avoids a synchronised ClassLoader lookup ({@code Class.forName}) on every
     * single block removal – previously this happened inside the event handler.
     */
    private static final boolean IS_FOLIA;
    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionConfiguration");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private final CounterManager counterManager;
    private final ConfigManager  configManager;

    // WorldUUID -> ChunkKey -> BlockKey -> PlayerUUID
    private final Map<UUID, Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<UUID>>> blockOwners = new ConcurrentHashMap<>();

    // Blocks silenced via the DISABLE action – short-circuits repeated events for the same block.
    // Map: WorldUUID -> BlockKeys
    private final Map<UUID, LongOpenHashSet> disabledBlocks = new ConcurrentHashMap<>();

    private volatile Set<Material>               cachedRedstoneMaterials;
    private volatile Set<String>                 cachedEnabledWorlds;
    private volatile ConfigManager.RemovalAction cachedRemovalAction;

    public RedstoneListener(CounterManager counterManager, ConfigManager configManager) {
        this.counterManager = counterManager;
        this.configManager  = configManager;
        refreshCache();
    }

    public void refreshCache() {
        this.cachedRedstoneMaterials = configManager.getRedstoneMaterials();
        this.cachedEnabledWorlds     = configManager.getEnabledWorlds();
        this.cachedRemovalAction     = configManager.getRemovalAction();
    }

    // -----------------------------------------------------------------------
    // Block placement – record owner for bypass / warning purposes
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        long chunkKey = CounterManager.packChunk(block.getX() >> 4, block.getZ() >> 4);
        long blockKey = CounterManager.packBlock(block.getX(), block.getY(), block.getZ());

        blockOwners.computeIfAbsent(block.getWorld().getUID(), k -> new Long2ObjectOpenHashMap<>())
                   .computeIfAbsent(chunkKey, k -> new Long2ObjectOpenHashMap<>())
                   .put(blockKey, event.getPlayer().getUniqueId());
    }

    // -----------------------------------------------------------------------
    // Main redstone handler
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Block    block    = event.getBlock();
        Material material = block.getType();
        World    world    = block.getWorld();
        UUID     worldId  = world.getUID();

        // --- Fast-reject filters (no lock, no allocation) ---
        if (!cachedEnabledWorlds.contains("*") && !cachedEnabledWorlds.contains(world.getName())) return;
        if (!cachedRedstoneMaterials.contains(material)) return;

        int  blockX    = block.getX();
        int  blockY    = block.getY();
        int  blockZ    = block.getZ();
        long blockKey  = CounterManager.packBlock(blockX, blockY, blockZ);
        long chunkKey  = CounterManager.packChunk(blockX >> 4, blockZ >> 4);

        // Short-circuit already-disabled blocks (O(1), no lock)
        LongOpenHashSet worldDisabled = disabledBlocks.get(worldId);
        if (worldDisabled != null && worldDisabled.contains(blockKey)) {
            event.setNewCurrent(0);
            return;
        }

        // Lockdown check (O(1), dedicated lockdownLock – does not block counter ops)
        if (counterManager.isChunkLocked(worldId, chunkKey)) {
            event.setNewCurrent(0);
            return;
        }

        // Owner bypass
        if (hasOwnerBypass(worldId, chunkKey, blockKey)) return;

        // Whitelist (rarely enabled – checked last to avoid unnecessary work)
        if (configManager.isWhitelistEnabled()) {
            String strChunkKey = world.getName() + ":" + (blockX >> 4) + ":" + (blockZ >> 4);
            if (!configManager.isChunkWhitelisted(strChunkKey)) return;
        }

        // --- Single lock: increment + warn-check + disable-check ---
        int result = counterManager.processEvent(worldId, chunkKey, blockKey);

        if (result == CounterManager.EVENT_WARN) {
            UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
            if (ownerUuid != null) {
                counterManager.sendWarning(block.getLocation(), material, ownerUuid);
            }

        } else if (result == CounterManager.EVENT_DISABLE) {

            // Trigger chunk lockdown and notify players asynchronously
            if (configManager.isLockdownEnabled()) {
                int duration = configManager.getLockdownDurationSeconds();
                if (counterManager.lockdownChunk(worldId, chunkKey, duration)) {
                    broadcastLockdownNotification(worldId, world.getName(), chunkKey, duration);
                }
            }

            applyRemovalAction(block, material, event, worldId, blockKey);

            // Remove owner record for this block
            Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
            if (worldOwners != null) {
                Long2ObjectOpenHashMap<UUID> chunkOwners = worldOwners.get(chunkKey);
                if (chunkOwners != null) {
                    chunkOwners.remove(blockKey);
                    if (chunkOwners.isEmpty()) worldOwners.remove(chunkKey);
                }
            }

            counterManager.handleRedstoneRemoval(block.getLocation(), material, chunkKey, blockKey);
        }
    }

    // -----------------------------------------------------------------------
    // Lockdown notification (async – keeps O(N players) off the main thread)
    // -----------------------------------------------------------------------

    /**
     * Builds the lockdown messages on the calling (main/region) thread where
     * component construction is safe, then dispatches the player-iteration loop
     * to an async task so the main thread is not stalled.
     *
     * <p>{@code Player.sendMessage()} is async-safe in Paper/Spigot 1.17+ and
     * within Folia's async scheduler.</p>
     */
    private void broadcastLockdownNotification(UUID worldId, String worldName,
                                                long chunkKey, int duration) {
        int chunkX = (int) (chunkKey >> 32);
        int chunkZ = (int) chunkKey;

        // Build components on the current thread (MessageManager is not thread-safe).
        final net.kyori.adventure.text.Component adminMsg =
                counterManager.getPlugin().getMessageManager()
                        .getMessage("alerts.chunk-lockdown-admin")
                        .replaceText(t -> t.matchLiteral("{world}").replacement(worldName))
                        .replaceText(t -> t.matchLiteral("{chunkX}").replacement(String.valueOf(chunkX)))
                        .replaceText(t -> t.matchLiteral("{chunkZ}").replacement(String.valueOf(chunkZ)))
                        .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration)));

        final net.kyori.adventure.text.Component localMsg =
                counterManager.getPlugin().getMessageManager()
                        .getMessage("alerts.chunk-lockdown-local")
                        .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration)));

        // Dispatch the O(N players) loop asynchronously via the plugin's scheduler abstraction
        // (handles both Bukkit and Folia transparently).
        counterManager.getPlugin().getScheduler().runTaskAsynchronously(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("antiredstonelag.alerts")) {
                    p.sendMessage(adminMsg);
                }
                if (p.getWorld().getUID().equals(worldId)) {
                    int pChunkX = p.getLocation().getBlockX() >> 4;
                    int pChunkZ = p.getLocation().getBlockZ() >> 4;
                    if (Math.abs(pChunkX - chunkX) <= 1 && Math.abs(pChunkZ - chunkZ) <= 1) {
                        if (!p.hasPermission("antiredstonelag.alerts")) {
                            p.sendMessage(localMsg);
                        }
                    }
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private UUID getOwner(UUID worldId, long chunkKey, long blockKey) {
        Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) {
            Long2ObjectOpenHashMap<UUID> chunkOwners = worldOwners.get(chunkKey);
            if (chunkOwners != null) return chunkOwners.get(blockKey);
        }
        return null;
    }

    private void applyRemovalAction(Block block, Material material, BlockRedstoneEvent event,
                                    UUID worldId, long blockKey) {
        event.setNewCurrent(0);
        disabledBlocks.computeIfAbsent(worldId, k -> new LongOpenHashSet()).add(blockKey);

        if (block.getType() == Material.AIR || cachedRemovalAction == ConfigManager.RemovalAction.DISABLE) {
            return;
        }

        Location loc  = block.getLocation();
        Runnable task = () -> {
            if (block.getType() == Material.AIR) return;
            if (cachedRemovalAction == ConfigManager.RemovalAction.REMOVE) {
                block.setType(Material.AIR, false);
            } else if (cachedRemovalAction == ConfigManager.RemovalAction.DROP) {
                block.breakNaturally();
            }
        };

        // Use the cached IS_FOLIA flag – avoids Class.forName on every removal.
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().execute(counterManager.getPlugin(), loc, task);
        } else {
            Bukkit.getScheduler().runTask(counterManager.getPlugin(), task);
        }
    }

    private boolean hasOwnerBypass(UUID worldId, long chunkKey, long blockKey) {
        UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
        if (ownerUuid != null) {
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null && owner.hasPermission(BYPASS_PERMISSION)) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Chunk cleanup
    // -----------------------------------------------------------------------

    public void cleanupChunk(Chunk chunk) {
        UUID worldId  = chunk.getWorld().getUID();
        long chunkKey = CounterManager.packChunk(chunk.getX(), chunk.getZ());
        Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) worldOwners.remove(chunkKey);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        cleanupChunk(event.getChunk());
    }

    public void clearDisabledBlocks() {
        disabledBlocks.values().forEach(LongOpenHashSet::clear);
    }

    // -----------------------------------------------------------------------
    // Player interaction (lockdown gate)
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        UUID worldId  = block.getWorld().getUID();
        long chunkKey = CounterManager.packChunk(block.getX() >> 4, block.getZ() >> 4);
        if (counterManager.isChunkLocked(worldId, chunkKey)) {
            long remaining = counterManager.getLockdownRemaining(worldId, chunkKey) / 1000;
            event.setCancelled(true);
            event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                    "§c[!] Redstone in this chunk is on lockdown for " + remaining + "s due to extreme lag."));
        }
    }
}