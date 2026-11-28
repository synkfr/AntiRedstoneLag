package org.ayosynk.antiRedstoneLag.listener;

import net.kyori.adventure.text.Component;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.engine.AdaptiveMonitor;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.engine.WorldRuleSet;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listens for redstone, piston, crafter, hopper, minecart-cluster, and chunk lifecycle events.
 */
public class RedstoneListener implements Listener {
    private static final String BYPASS_PERMISSION = "antiredstonelag.bypass";

    private final AntiRedstoneLag plugin;
    private final RedstoneEngine engine;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private volatile boolean cachedRedstoneEnabled;
    private volatile Set<Material> cachedRedstoneMaterials;
    private volatile Set<String> cachedEnabledWorlds;

    public RedstoneListener(AntiRedstoneLag plugin, RedstoneEngine engine, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.engine = engine;
        this.configManager = configManager;
        this.messageManager = messageManager;
        refreshCache();
    }

    public void refreshCache() {
        this.cachedRedstoneEnabled = configManager.isRedstoneEnabled();
        this.cachedRedstoneMaterials = configManager.getRedstoneMaterials();
        this.cachedEnabledWorlds = configManager.getEnabledWorlds();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        UUID worldId = block.getWorld().getUID();
        long chunkKey = BlockKeyCodec.packChunk(block.getX() >> 4, block.getZ() >> 4);
        long blockKey = BlockKeyCodec.packBlock(block.getX(), block.getY(), block.getZ());
        Player player = event.getPlayer();

        engine.setBlockOwner(worldId, chunkKey, blockKey, player.getUniqueId());
        engine.cachePlayerBypass(player.getUniqueId(), player.hasPermission(BYPASS_PERMISSION));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        clearTrackedBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            clearTrackedBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            clearTrackedBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        engine.hydrateChunkOwners(chunk.getWorld().getUID(), BlockKeyCodec.packChunk(chunk.getX(), chunk.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        engine.unloadChunkCache(chunk.getWorld().getUID(), BlockKeyCodec.packChunk(chunk.getX(), chunk.getZ()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        evaluateRedstoneEvent(event.getBlock(), event.getBlock().getType(), null, event, "Exceeded threshold / Lag machine");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        if (!cachedRedstoneEnabled) return;
        UUID worldId = piston.getWorld().getUID();
        long blockKey = BlockKeyCodec.packBlock(piston.getX(), piston.getY(), piston.getZ());

        if (engine.checkPistonCycleExceeded(worldId, blockKey)) {
            long chunkKey = BlockKeyCodec.packChunk(piston.getX() >> 4, piston.getZ() >> 4);
            if (!engine.hasOwnerBypass(worldId, chunkKey, blockKey)) {
                WorldRuleSet rules = WorldRuleSet.resolve(configManager, piston.getWorld().getName());
                if (rules.enabled() && rules.removalAction() != PluginConfig.RemovalAction.NOTIFY) {
                    event.setCancelled(true);
                    engine.freezeBlock(worldId, piston.getWorld().getName(), chunkKey, blockKey, piston.getType(),
                            engine.getOwner(worldId, chunkKey, blockKey), configManager.getPluginConfig().getFreeze().getDurationSeconds());
                    return;
                }
            }
        }

        evaluateRedstoneEvent(piston, piston.getType(), event, null, "Piston 0-tick spam");
        if (!event.isCancelled()) {
            shiftMovedBlockOwners(event.getBlocks(), event.getDirection());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block piston = event.getBlock();
        if (!cachedRedstoneEnabled) return;
        evaluateRedstoneEvent(piston, piston.getType(), event, null, "Piston 0-tick spam");
        if (!event.isCancelled()) {
            shiftMovedBlockOwners(event.getBlocks(), event.getDirection());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (!cachedRedstoneEnabled) return;
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!isWorldMonitored(world.getName())) return;
        WorldRuleSet rules = WorldRuleSet.resolve(configManager, world.getName());
        if (!rules.enabled() || rules.removalAction() == PluginConfig.RemovalAction.NOTIFY) return;

        UUID worldId = world.getUID();
        long chunkKey = BlockKeyCodec.packChunk(block.getX() >> 4, block.getZ() >> 4);
        long blockKey = BlockKeyCodec.packBlock(block.getX(), block.getY(), block.getZ());
        if (engine.hasOwnerBypass(worldId, chunkKey, blockKey)) return;

        if (engine.checkCrafterCraftExceeded(worldId, blockKey)) {
            event.setCancelled(true);
            engine.freezeBlock(worldId, world.getName(), chunkKey, blockKey, block.getType(),
                    engine.getOwner(worldId, chunkKey, blockKey), configManager.getPluginConfig().getFreeze().getDurationSeconds());
            if (engine.canSendAlert()) {
                String raw = messageManager.getMessagesConfig().getAlerts().getCrafterThrottled();
                Component msg = messageManager.parseMessage(raw, Map.of(
                        "x", String.valueOf(block.getX()),
                        "y", String.valueOf(block.getY()),
                        "z", String.valueOf(block.getZ()),
                        "world", world.getName()
                ));
                engine.broadcastAlert(raw, msg);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!cachedRedstoneEnabled) return;
        if (!configManager.getPluginConfig().getDetectors().getHopperLimiter().isEnabled()) return;
        Location loc = event.getSource().getLocation();
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        if (!isWorldMonitored(world.getName())) return;

        WorldRuleSet rules = WorldRuleSet.resolve(configManager, world.getName());
        if (!rules.enabled() || rules.removalAction() == PluginConfig.RemovalAction.NOTIFY) return;

        UUID worldId = world.getUID();
        long chunkKey = BlockKeyCodec.packChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        long blockKey = BlockKeyCodec.packBlock(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (engine.hasOwnerBypass(worldId, chunkKey, blockKey)) return;

        if (engine.checkHopperTransferExceeded(worldId, blockKey)) {
            event.setCancelled(true);
            if (engine.canSendAlert()) {
                String raw = messageManager.getMessagesConfig().getAlerts().getHopperThrottled();
                Component msg = messageManager.parseMessage(raw, Map.of(
                        "x", String.valueOf(loc.getBlockX()),
                        "y", String.valueOf(loc.getBlockY()),
                        "z", String.valueOf(loc.getBlockZ()),
                        "world", world.getName()
                ));
                engine.broadcastAlert(raw, msg);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (event.getVehicle() instanceof Minecart cart) {
            checkMinecartCluster(cart);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleCollision(VehicleEntityCollisionEvent event) {
        if (event.getVehicle() instanceof Minecart cart && event.getEntity() instanceof Minecart) {
            checkMinecartCluster(cart);
        }
    }

    private void checkMinecartCluster(Minecart cart) {
        if (!cachedRedstoneEnabled) return;
        var mcCfg = configManager.getPluginConfig().getDetectors().getMinecartCluster();
        if (!mcCfg.isEnabled()) return;

        Location loc = cart.getLocation();
        World world = loc.getWorld();
        if (world == null || !isWorldMonitored(world.getName())) return;

        int maxPerBlock = mcCfg.getMaxMinecartsPerBlock();
        List<Minecart> stacked = new ArrayList<>();
        for (Entity nearby : world.getNearbyEntities(loc, 0.75, 0.75, 0.75)) {
            if (nearby instanceof Minecart m && m.isValid() && m.getPassengers().isEmpty()) {
                stacked.add(m);
            }
        }
        if (stacked.size() <= maxPerBlock) return;

        var action = mcCfg.getAction();
        int removed = 0;
        if (action == PluginConfig.MinecartClusterAction.REMOVE_EXCESS
                || action == PluginConfig.MinecartClusterAction.FREEZE_OR_REMOVE) {
            for (int i = maxPerBlock; i < stacked.size(); i++) {
                Minecart target = stacked.get(i);
                plugin.getScheduler().runForEntity(target, () -> {
                    if (target.isValid() && target.getPassengers().isEmpty()) {
                        target.remove();
                    }
                }, null);
                removed++;
            }
        }

        if (engine.canSendAlert()) {
            String raw = messageManager.getMessagesConfig().getAlerts().getMinecartCluster();
            Component msg = messageManager.parseMessage(raw, Map.of(
                    "count", String.valueOf(Math.max(1, removed)),
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ()),
                    "world", world.getName()
            ));
            engine.broadcastAlert(raw, msg);
        }
    }

    private void evaluateRedstoneEvent(Block block, Material material, Cancellable cancellable,
                                       BlockRedstoneEvent redstoneEvent, String snapshotReason) {
        if (!cachedRedstoneEnabled) return;

        World world = block.getWorld();
        String worldName = world.getName();
        if (!isWorldMonitored(worldName)) return;
        if (!cachedRedstoneMaterials.contains(material)) return;

        WorldRuleSet rules = WorldRuleSet.resolve(configManager, worldName);
        if (!rules.enabled()) return;

        UUID worldId = world.getUID();
        int blockX = block.getX();
        int blockY = block.getY();
        int blockZ = block.getZ();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long blockKey = BlockKeyCodec.packBlock(blockX, blockY, blockZ);
        long chunkKey = BlockKeyCodec.packChunk(chunkX, chunkZ);

        PluginConfig.RemovalAction action = rules.removalAction();
        if (action != PluginConfig.RemovalAction.NOTIFY) {
            if (engine.isBlockDisabled(worldId, chunkKey, blockKey)
                    || engine.isBlockFrozen(worldId, blockKey)
                    || engine.isChunkLocked(worldId, chunkKey)) {
                cancelSignal(cancellable, redstoneEvent);
                return;
            }
        }

        if (engine.hasOwnerBypass(worldId, chunkKey, blockKey)) return;
        if (!configManager.isChunkWhitelisted(worldName, chunkX, chunkZ)) return;

        boolean hasNearbyPlayers = hasPlayersNearby(world, blockX, blockY, blockZ);
        int result = engine.processRedstoneEvent(world, chunkKey, blockKey, material, hasNearbyPlayers);

        if (result == RedstoneEngine.EVENT_WARN) {
            UUID ownerUuid = engine.getOwner(worldId, chunkKey, blockKey);
            if (ownerUuid != null) {
                engine.sendOwnerWarning(block.getLocation(), material, ownerUuid);
            }
        } else if (result == RedstoneEngine.EVENT_NOTIFY) {
            UUID ownerUuid = engine.getOwner(worldId, chunkKey, blockKey);
            engine.handleRedstoneNotify(block.getLocation(), material, chunkKey, blockKey, ownerUuid);
        } else if (result == RedstoneEngine.EVENT_FREEZE) {
            cancelSignal(cancellable, redstoneEvent);
            UUID ownerUuid = engine.getOwner(worldId, chunkKey, blockKey);
            if (engine.getSnapshotService() != null) {
                engine.getSnapshotService().takeSnapshot(
                        block, material,
                        engine.getChunkUpdates(worldId, chunkKey),
                        engine.getBlockUpdates(worldId, blockKey),
                        ownerUuid, "Clock throttled/frozen");
            }
            if (ownerUuid != null && configManager.isAlertsEnabled()) {
                if (!configManager.isOnlyWhenLagging() || AdaptiveMonitor.isFolia() || engine.getAdaptiveMonitor().isServerLagging()) {
                    Player owner = Bukkit.getPlayer(ownerUuid);
                    if (owner != null && owner.isOnline()) {
                        String raw = messageManager.getMessagesConfig().getAlerts().getClockFrozen();
                        int duration = configManager.getPluginConfig().getFreeze().getDurationSeconds();
                        Component msg = messageManager.parseMessage(raw, Map.of(
                                "x", String.valueOf(blockX),
                                "y", String.valueOf(blockY),
                                "z", String.valueOf(blockZ),
                                "duration", String.valueOf(duration)
                        ));
                        messageManager.sendIfNotBlank(owner, raw, msg);
                    }
                }
            }
        } else if (result == RedstoneEngine.EVENT_DISABLE) {
            UUID ownerUuid = engine.getOwner(worldId, chunkKey, blockKey);
            if (engine.getSnapshotService() != null) {
                engine.getSnapshotService().takeSnapshot(
                        block, material,
                        engine.getChunkUpdates(worldId, chunkKey),
                        engine.getBlockUpdates(worldId, blockKey),
                        ownerUuid, snapshotReason);
            }

            if (configManager.isLockdownEnabled()) {
                int duration = configManager.getLockdownDurationSeconds();
                if (engine.lockdownChunk(worldId, worldName, chunkKey, duration)) {
                    broadcastLockdownNotification(world, worldName, blockX, blockY, blockZ, chunkX, chunkZ, duration);
                }
            }

            applyAction(block, cancellable, redstoneEvent, worldId, chunkKey, blockKey, action);
            if (action == PluginConfig.RemovalAction.REMOVE || action == PluginConfig.RemovalAction.DROP) {
                engine.removeBlockOwner(worldId, chunkKey, blockKey);
            }

            engine.handleRedstoneMitigation(block.getLocation(), material, chunkKey, blockKey, action);
        }
    }

    private void shiftMovedBlockOwners(List<Block> blocks, BlockFace direction) {
        if (blocks == null || blocks.isEmpty()) return;
        for (Block block : blocks) {
            if (!cachedRedstoneMaterials.contains(block.getType())) continue;
            UUID worldId = block.getWorld().getUID();
            long oldChunk = BlockKeyCodec.packChunk(block.getX() >> 4, block.getZ() >> 4);
            long oldKey = BlockKeyCodec.packBlock(block.getX(), block.getY(), block.getZ());
            UUID owner = engine.getOwner(worldId, oldChunk, oldKey);
            if (owner != null) {
                engine.removeBlockOwner(worldId, oldChunk, oldKey);
                int nx = block.getX() + direction.getModX();
                int ny = block.getY() + direction.getModY();
                int nz = block.getZ() + direction.getModZ();
                long newChunk = BlockKeyCodec.packChunk(nx >> 4, nz >> 4);
                long newKey = BlockKeyCodec.packBlock(nx, ny, nz);
                engine.setBlockOwner(worldId, newChunk, newKey, owner);
            }
        }
    }

    private void clearTrackedBlock(Block block) {
        if (block == null || !cachedRedstoneMaterials.contains(block.getType())) return;
        UUID worldId = block.getWorld().getUID();
        long chunkKey = BlockKeyCodec.packChunk(block.getX() >> 4, block.getZ() >> 4);
        long blockKey = BlockKeyCodec.packBlock(block.getX(), block.getY(), block.getZ());
        engine.removeBlockOwner(worldId, chunkKey, blockKey);
        engine.unfreezeBlock(worldId, blockKey);
    }

    private boolean isWorldMonitored(String worldName) {
        return cachedEnabledWorlds.contains("*") || cachedEnabledWorlds.contains(worldName);
    }

    private static void cancelSignal(Cancellable cancellable, BlockRedstoneEvent redstoneEvent) {
        if (redstoneEvent != null) {
            redstoneEvent.setNewCurrent(0);
        }
        if (cancellable != null) {
            cancellable.setCancelled(true);
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

    private void broadcastLockdownNotification(World world, String worldName, int bx, int by, int bz,
                                             int chunkX, int chunkZ, int duration) {
        if (!configManager.isAlertsEnabled()) return;
        if (configManager.isOnlyWhenLagging() && !AdaptiveMonitor.isFolia() && !engine.getAdaptiveMonitor().isServerLagging()) {
            return;
        }

        String rawAdmin = messageManager.getMessagesConfig().getAlerts().getChunkLockdownAdmin();
        Component adminMsg = messageManager.parseMessage(rawAdmin, Map.of(
                "world", worldName,
                "chunkX", String.valueOf(chunkX),
                "chunkZ", String.valueOf(chunkZ),
                "duration", String.valueOf(duration)
        ));

        String rawLocal = messageManager.getMessagesConfig().getAlerts().getChunkLockdownLocal();
        Component localMsg = messageManager.parseMessage(rawLocal, Map.of(
                "duration", String.valueOf(duration)
        ));

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

        if (engine.getWebhookService() != null) {
            engine.getWebhookService().sendAlert("LOCKDOWN", "Chunk Lockdown Triggered",
                    String.format("World: %s | Chunk: %d, %d | Duration: %ds", worldName, chunkX, chunkZ, duration));
        }
    }

    private void applyAction(Block block, Cancellable cancellable, BlockRedstoneEvent redstoneEvent,
                             UUID worldId, long chunkKey, long blockKey, PluginConfig.RemovalAction action) {
        cancelSignal(cancellable, redstoneEvent);
        engine.markBlockDisabled(worldId, chunkKey, blockKey);

        if (action == PluginConfig.RemovalAction.FREEZE
                || action == PluginConfig.RemovalAction.DISABLE
                || action == PluginConfig.RemovalAction.NOTIFY
                || block.getType() == Material.AIR) {
            return;
        }

        Location loc = block.getLocation();
        plugin.getScheduler().runAtLocation(loc, () -> {
            if (block.getType() == Material.AIR) return;
            if (action == PluginConfig.RemovalAction.REMOVE) {
                block.setType(Material.AIR, false);
            } else if (action == PluginConfig.RemovalAction.DROP) {
                block.breakNaturally();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!cachedRedstoneEnabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        WorldRuleSet rules = WorldRuleSet.resolve(configManager, block.getWorld().getName());
        if (!rules.enabled() || rules.removalAction() == PluginConfig.RemovalAction.NOTIFY) return;

        UUID worldId = block.getWorld().getUID();
        long blockKey = BlockKeyCodec.packBlock(block.getX(), block.getY(), block.getZ());
        long chunkKey = BlockKeyCodec.packChunk(block.getX() >> 4, block.getZ() >> 4);

        if (engine.isBlockFrozen(worldId, blockKey)) {
            long remaining = Math.max(1L, engine.getFreezeRemainingMillis(worldId, blockKey) / 1000L);
            event.setCancelled(true);
            String raw = messageManager.getMessagesConfig().getAlerts().getComponentPaused();
            messageManager.sendIfNotBlank(event.getPlayer(), raw, Map.of("duration", String.valueOf(remaining)));
            return;
        }

        if (engine.isChunkLocked(worldId, chunkKey)) {
            long remaining = Math.max(1L, engine.getLockdownRemainingMillis(worldId, chunkKey) / 1000L);
            event.setCancelled(true);
            String raw = messageManager.getMessagesConfig().getAlerts().getChunkLockedInteract();
            messageManager.sendIfNotBlank(event.getPlayer(), raw, Map.of("duration", String.valueOf(remaining)));
        }
    }
}
