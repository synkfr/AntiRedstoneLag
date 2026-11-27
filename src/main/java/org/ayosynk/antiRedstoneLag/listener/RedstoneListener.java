package org.ayosynk.antiRedstoneLag.listener;

import net.kyori.adventure.text.Component;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedstoneListener implements Listener {
    private static final String BYPASS_PERMISSION = "antiredstonelag.bypass";

    private final CounterManager counterManager;
    private final ConfigManager configManager;

    private final Map<UUID, ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>>> blockOwners = new ConcurrentHashMap<>();
    private final Map<UUID, ConcurrentHashMap<Long, LongOpenHashSet>> disabledBlocks = new ConcurrentHashMap<>();

    private volatile boolean cachedRedstoneEnabled;
    private volatile Set<Material> cachedRedstoneMaterials;
    private volatile Set<String> cachedEnabledWorlds;
    private volatile PluginConfig.RemovalAction cachedRemovalAction;

    public RedstoneListener(CounterManager counterManager, ConfigManager configManager) {
        this.counterManager = counterManager;
        this.configManager = configManager;
        refreshCache();
    }

    public void refreshCache() {
        this.cachedRedstoneEnabled = configManager.isRedstoneEnabled();
        this.cachedRedstoneMaterials = configManager.getRedstoneMaterials();
        this.cachedEnabledWorlds = configManager.getEnabledWorlds();
        this.cachedRemovalAction = configManager.getRemovalAction();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        long chunkKey = CounterManager.packChunk(block.getX() >> 4, block.getZ() >> 4);
        long blockKey = CounterManager.packBlock(block.getX(), block.getY(), block.getZ());

        Long2ObjectOpenHashMap<UUID> chunkOwners = blockOwners
                .computeIfAbsent(block.getWorld().getUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> new Long2ObjectOpenHashMap<>());
        synchronized (chunkOwners) {
            chunkOwners.put(blockKey, event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        removeOwner(block.getWorld().getUID(),
                CounterManager.packChunk(block.getX() >> 4, block.getZ() >> 4),
                CounterManager.packBlock(block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        evaluateRedstoneEvent(event.getBlock(), event.getBlock().getType(), null, event, "Exceeded threshold / Lag machine");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        evaluateRedstoneEvent(event.getBlock(), event.getBlock().getType(), event, null, "Piston 0-tick spam");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        evaluateRedstoneEvent(event.getBlock(), event.getBlock().getType(), event, null, "Piston 0-tick spam");
    }

    private void evaluateRedstoneEvent(Block block, Material material, Cancellable cancellable,
                                       BlockRedstoneEvent redstoneEvent, String snapshotReason) {
        if (!cachedRedstoneEnabled) return;

        World world = block.getWorld();
        UUID worldId = world.getUID();

        if (!cachedEnabledWorlds.contains("*") && !cachedEnabledWorlds.contains(world.getName())) return;
        if (!cachedRedstoneMaterials.contains(material)) return;

        int blockX = block.getX();
        int blockY = block.getY();
        int blockZ = block.getZ();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long blockKey = CounterManager.packBlock(blockX, blockY, blockZ);
        long chunkKey = CounterManager.packChunk(chunkX, chunkZ);

        PluginConfig.RemovalAction action = cachedRemovalAction;
        if (action != PluginConfig.RemovalAction.NOTIFY) {
            if (isBlockDisabled(worldId, chunkKey, blockKey)
                    || counterManager.isBlockFrozen(worldId, blockKey)
                    || counterManager.isChunkLocked(worldId, chunkKey)) {
                cancelSignal(cancellable, redstoneEvent);
                return;
            }
        }

        if (hasOwnerBypass(worldId, chunkKey, blockKey)) return;
        if (!configManager.isChunkWhitelisted(world.getName(), chunkX, chunkZ)) return;

        boolean hasNearbyPlayers = hasPlayersNearby(world, blockX, blockY, blockZ);
        int result = counterManager.processEvent(worldId, chunkKey, blockKey, hasNearbyPlayers);

        if (result == CounterManager.EVENT_WARN) {
            UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
            if (ownerUuid != null) {
                counterManager.sendWarning(block.getLocation(), material, ownerUuid);
            }
        } else if (result == CounterManager.EVENT_NOTIFY) {
            UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
            counterManager.handleRedstoneNotify(block.getLocation(), material, chunkKey, blockKey, ownerUuid);
        } else if (result == CounterManager.EVENT_FREEZE) {
            cancelSignal(cancellable, redstoneEvent);
            UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
            if (counterManager.getPlugin().getSnapshotManager() != null) {
                counterManager.getPlugin().getSnapshotManager().takeSnapshot(
                        block, material,
                        counterManager.getChunkUpdates(worldId, chunkKey),
                        counterManager.getBlockUpdates(worldId, blockKey),
                        ownerUuid, "Clock throttled/frozen");
            }
            if (ownerUuid != null && configManager.isAlertsEnabled()) {
                if (!configManager.isOnlyWhenLagging() || CounterManager.isFolia() || counterManager.isServerLagging()) {
                    Player owner = Bukkit.getPlayer(ownerUuid);
                    if (owner != null && owner.isOnline()) {
                        MessageManager mm = counterManager.getPlugin().getMessageManager();
                        String raw = mm.getMessagesConfig().getAlerts().getClockFrozen();
                        int duration = configManager.getPluginConfig().getFreeze().getDurationSeconds();
                        Component msg = mm.parseMessage(raw)
                                .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(blockX)))
                                .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(blockY)))
                                .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(blockZ)))
                                .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration)));
                        mm.sendIfNotBlank(owner, raw, msg);
                    }
                }
            }
        } else if (result == CounterManager.EVENT_DISABLE) {
            UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
            if (counterManager.getPlugin().getSnapshotManager() != null) {
                counterManager.getPlugin().getSnapshotManager().takeSnapshot(
                        block, material,
                        counterManager.getChunkUpdates(worldId, chunkKey),
                        counterManager.getBlockUpdates(worldId, blockKey),
                        ownerUuid, snapshotReason);
            }

            if (configManager.isLockdownEnabled()) {
                int duration = configManager.getLockdownDurationSeconds();
                if (counterManager.lockdownChunk(worldId, chunkKey, duration)) {
                    broadcastLockdownNotification(world, world.getName(), blockX, blockY, blockZ, chunkKey, duration);
                }
            }

            applyAction(block, cancellable, redstoneEvent, worldId, chunkKey, blockKey, action);
            if (action == PluginConfig.RemovalAction.REMOVE || action == PluginConfig.RemovalAction.DROP) {
                removeOwner(worldId, chunkKey, blockKey);
            }

            counterManager.handleRedstoneRemoval(block.getLocation(), material, chunkKey, blockKey);
        }
    }

    private void cancelSignal(Cancellable cancellable, BlockRedstoneEvent redstoneEvent) {
        if (redstoneEvent != null) {
            redstoneEvent.setNewCurrent(0);
        }
        if (cancellable != null) {
            cancellable.setCancelled(true);
        }
    }

    private boolean isBlockDisabled(UUID worldId, long chunkKey, long blockKey) {
        ConcurrentHashMap<Long, LongOpenHashSet> worldDisabled = disabledBlocks.get(worldId);
        if (worldDisabled == null) return false;
        LongOpenHashSet chunkDisabled = worldDisabled.get(chunkKey);
        if (chunkDisabled == null) return false;
        synchronized (chunkDisabled) {
            return chunkDisabled.contains(blockKey);
        }
    }

    private boolean hasPlayersNearby(World world, int x, int y, int z) {
        int radius = configManager.getPluginConfig().getProximity().getPlayerRadius();
        Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        try {
            Collection<Player> nearby = world.getNearbyPlayers(loc, radius, radius, radius);
            return !nearby.isEmpty();
        } catch (Throwable t) {
            int radiusSq = radius * radius;
            for (Player player : world.getPlayers()) {
                if (Bukkit.isOwnedByCurrentRegion(player)) {
                    Location pLoc = player.getLocation();
                    double dx = pLoc.getX() - x;
                    double dy = pLoc.getY() - y;
                    double dz = pLoc.getZ() - z;
                    if ((dx * dx + dy * dy + dz * dz) <= radiusSq) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private void broadcastLockdownNotification(World world, String worldName, int bx, int by, int bz, long chunkKey, int duration) {
        if (!configManager.isAlertsEnabled()) return;
        if (configManager.isOnlyWhenLagging() && !CounterManager.isFolia() && !counterManager.isServerLagging()) return;

        int chunkX = (int) (chunkKey >> 32);
        int chunkZ = (int) chunkKey;
        MessageManager mm = counterManager.getPlugin().getMessageManager();

        String rawAdmin = mm.getMessagesConfig().getAlerts().getChunkLockdownAdmin();
        Component adminMsg = mm.parseMessage(rawAdmin)
                .replaceText(t -> t.matchLiteral("{world}").replacement(worldName))
                .replaceText(t -> t.matchLiteral("{chunkX}").replacement(String.valueOf(chunkX)))
                .replaceText(t -> t.matchLiteral("{chunkZ}").replacement(String.valueOf(chunkZ)))
                .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration)));

        String rawLocal = mm.getMessagesConfig().getAlerts().getChunkLockdownLocal();
        Component localMsg = mm.parseMessage(rawLocal)
                .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration)));

        // Gather local players on the block's owning region thread to avoid async Player.getLocation().
        List<UUID> nearbyNonAdmins = new ArrayList<>();
        try {
            Location center = new Location(world, bx + 0.5, by + 0.5, bz + 0.5);
            for (Player p : world.getNearbyPlayers(center, 24.0, 24.0, 24.0)) {
                if (!p.hasPermission("antiredstonelag.alerts")) {
                    nearbyNonAdmins.add(p.getUniqueId());
                }
            }
        } catch (Throwable ignored) {
        }

        if (rawAdmin != null && !rawAdmin.isBlank()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("antiredstonelag.alerts")) {
                    p.sendMessage(adminMsg);
                }
            }
        }
        if (rawLocal != null && !rawLocal.isBlank()) {
            for (UUID uid : nearbyNonAdmins) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(localMsg);
                }
            }
        }
    }

    public UUID getBlockOwner(UUID worldId, int x, int y, int z) {
        return getOwner(worldId, CounterManager.packChunk(x >> 4, z >> 4), CounterManager.packBlock(x, y, z));
    }

    private UUID getOwner(UUID worldId, long chunkKey, long blockKey) {
        ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) {
            Long2ObjectOpenHashMap<UUID> chunkOwners = worldOwners.get(chunkKey);
            if (chunkOwners != null) {
                synchronized (chunkOwners) {
                    return chunkOwners.get(blockKey);
                }
            }
        }
        return null;
    }

    private void removeOwner(UUID worldId, long chunkKey, long blockKey) {
        ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) {
            Long2ObjectOpenHashMap<UUID> chunkOwners = worldOwners.get(chunkKey);
            if (chunkOwners != null) {
                synchronized (chunkOwners) {
                    chunkOwners.remove(blockKey);
                    if (chunkOwners.isEmpty()) {
                        worldOwners.remove(chunkKey, chunkOwners);
                    }
                }
            }
        }
    }

    private void applyAction(Block block, Cancellable cancellable, BlockRedstoneEvent redstoneEvent,
                             UUID worldId, long chunkKey, long blockKey, PluginConfig.RemovalAction action) {
        cancelSignal(cancellable, redstoneEvent);
        LongOpenHashSet chunkDisabled = disabledBlocks
                .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> new LongOpenHashSet());
        synchronized (chunkDisabled) {
            chunkDisabled.add(blockKey);
        }

        // FREEZE and DISABLE never break or delete player blocks.
        if (action == PluginConfig.RemovalAction.FREEZE
                || action == PluginConfig.RemovalAction.DISABLE
                || action == PluginConfig.RemovalAction.NOTIFY
                || block.getType() == Material.AIR) {
            return;
        }

        Location loc = block.getLocation();
        Runnable task = () -> {
            if (block.getType() == Material.AIR) return;
            if (action == PluginConfig.RemovalAction.REMOVE) {
                block.setType(Material.AIR, false);
            } else if (action == PluginConfig.RemovalAction.DROP) {
                block.breakNaturally();
            }
        };

        try {
            Bukkit.getRegionScheduler().execute(counterManager.getPlugin(), loc, task);
        } catch (Throwable t) {
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

    public void cleanupChunk(Chunk chunk) {
        UUID worldId = chunk.getWorld().getUID();
        long chunkKey = CounterManager.packChunk(chunk.getX(), chunk.getZ());
        ConcurrentHashMap<Long, Long2ObjectOpenHashMap<UUID>> worldOwners = blockOwners.get(worldId);
        if (worldOwners != null) worldOwners.remove(chunkKey);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        cleanupChunk(event.getChunk());
    }

    public void clearDisabledBlocks() {
        for (ConcurrentHashMap<Long, LongOpenHashSet> worldMap : disabledBlocks.values()) {
            worldMap.clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!cachedRedstoneEnabled || cachedRemovalAction == PluginConfig.RemovalAction.NOTIFY) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        UUID worldId = block.getWorld().getUID();
        long blockKey = CounterManager.packBlock(block.getX(), block.getY(), block.getZ());
        long chunkKey = CounterManager.packChunk(block.getX() >> 4, block.getZ() >> 4);
        MessageManager mm = counterManager.getPlugin().getMessageManager();

        if (counterManager.isBlockFrozen(worldId, blockKey)) {
            long remaining = counterManager.getFreezeRemaining(worldId, blockKey) / 1000;
            event.setCancelled(true);
            String raw = mm.getMessagesConfig().getAlerts().getComponentPaused();
            Component msg = mm.parseMessage(raw).replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(remaining)));
            mm.sendIfNotBlank(event.getPlayer(), raw, msg);
            return;
        }

        if (counterManager.isChunkLocked(worldId, chunkKey)) {
            long remaining = counterManager.getLockdownRemaining(worldId, chunkKey) / 1000;
            event.setCancelled(true);
            String raw = mm.getMessagesConfig().getAlerts().getChunkLockedInteract();
            Component msg = mm.parseMessage(raw).replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(remaining)));
            mm.sendIfNotBlank(event.getPlayer(), raw, msg);
        }
    }
}