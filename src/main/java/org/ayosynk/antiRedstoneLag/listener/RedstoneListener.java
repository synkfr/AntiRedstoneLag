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
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
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
    private final ConfigManager configManager;

    private final Map<UUID, Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<UUID>>> blockOwners = new ConcurrentHashMap<>();
    private final Map<UUID, LongOpenHashSet> disabledBlocks = new ConcurrentHashMap<>();

    private volatile Set<Material> cachedRedstoneMaterials;
    private volatile Set<String> cachedEnabledWorlds;
    private volatile ConfigManager.RemovalAction cachedRemovalAction;

    public RedstoneListener(CounterManager counterManager, ConfigManager configManager) {
        this.counterManager = counterManager;
        this.configManager = configManager;
        refreshCache();
    }

    public void refreshCache() {
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

        blockOwners.computeIfAbsent(block.getWorld().getUID(), k -> new Long2ObjectOpenHashMap<>())
                   .computeIfAbsent(chunkKey, k -> new Long2ObjectOpenHashMap<>())
                   .put(blockKey, event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        World world = block.getWorld();
        UUID worldId = world.getUID();

        if (!cachedEnabledWorlds.contains("*") && !cachedEnabledWorlds.contains(world.getName())) return;
        if (!cachedRedstoneMaterials.contains(material)) return;

        int blockX = block.getX();
        int blockY = block.getY();
        int blockZ = block.getZ();
        long blockKey = CounterManager.packBlock(blockX, blockY, blockZ);
        long chunkKey = CounterManager.packChunk(blockX >> 4, blockZ >> 4);

        LongOpenHashSet worldDisabled = disabledBlocks.get(worldId);
        if (worldDisabled != null && worldDisabled.contains(blockKey)) {
            event.setNewCurrent(0);
            return;
        }

        if (counterManager.isBlockFrozen(worldId, blockKey)) {
            event.setNewCurrent(0);
            return;
        }

        if (counterManager.isChunkLocked(worldId, chunkKey)) {
            event.setNewCurrent(0);
            return;
        }

        if (hasOwnerBypass(worldId, chunkKey, blockKey)) return;

        if (configManager.isWhitelistEnabled()) {
            String strChunkKey = world.getName() + ":" + (blockX >> 4) + ":" + (blockZ >> 4);
            if (!configManager.isChunkWhitelisted(strChunkKey)) return;
        }

        boolean hasNearbyPlayers = hasPlayersNearby(world, blockX, blockY, blockZ);
        int result = counterManager.processEvent(worldId, chunkKey, blockKey, hasNearbyPlayers);

        if (result == CounterManager.EVENT_WARN) {
            UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
            if (ownerUuid != null) {
                counterManager.sendWarning(block.getLocation(), material, ownerUuid);
            }
        } else if (result == CounterManager.EVENT_FREEZE) {
            event.setNewCurrent(0);
            UUID ownerUuid = getOwner(worldId, chunkKey, blockKey);
            if (ownerUuid != null) {
                Player owner = Bukkit.getPlayer(ownerUuid);
                if (owner != null && owner.isOnline()) {
                    int duration = configManager.getPluginConfig().getFreeze().getDurationSeconds();
                    owner.sendMessage(counterManager.getPlugin().getMessageManager().parseMessage(
                            counterManager.getPlugin().getMessageManager().getMessagesConfig().getAlerts().getClockFrozen())
                            .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(blockX)))
                            .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(blockY)))
                            .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(blockZ)))
                            .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration))));
                }
            }
        } else if (result == CounterManager.EVENT_DISABLE) {
            if (configManager.isLockdownEnabled()) {
                int duration = configManager.getLockdownDurationSeconds();
                if (counterManager.lockdownChunk(worldId, chunkKey, duration)) {
                    broadcastLockdownNotification(worldId, world.getName(), chunkKey, duration);
                }
            }

            applyRemovalAction(block, material, event, worldId, blockKey);

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

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePistonEvent(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePistonEvent(event.getBlock(), event);
    }

    private void handlePistonEvent(Block block, org.bukkit.event.Cancellable event) {
        World world = block.getWorld();
        UUID worldId = world.getUID();

        if (!cachedEnabledWorlds.contains("*") && !cachedEnabledWorlds.contains(world.getName())) return;
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        int blockX = block.getX();
        int blockY = block.getY();
        int blockZ = block.getZ();
        long blockKey = CounterManager.packBlock(blockX, blockY, blockZ);
        long chunkKey = CounterManager.packChunk(blockX >> 4, blockZ >> 4);

        LongOpenHashSet worldDisabled = disabledBlocks.get(worldId);
        if (worldDisabled != null && worldDisabled.contains(blockKey)) {
            event.setCancelled(true);
            return;
        }

        if (counterManager.isBlockFrozen(worldId, blockKey)) {
            event.setCancelled(true);
            return;
        }

        if (counterManager.isChunkLocked(worldId, chunkKey)) {
            event.setCancelled(true);
            return;
        }

        if (hasOwnerBypass(worldId, chunkKey, blockKey)) return;

        boolean hasNearbyPlayers = hasPlayersNearby(world, blockX, blockY, blockZ);
        int result = counterManager.processEvent(worldId, chunkKey, blockKey, hasNearbyPlayers);

        if (result == CounterManager.EVENT_FREEZE) {
            event.setCancelled(true);
        } else if (result == CounterManager.EVENT_DISABLE) {
            event.setCancelled(true);
            if (configManager.isLockdownEnabled()) {
                int duration = configManager.getLockdownDurationSeconds();
                if (counterManager.lockdownChunk(worldId, chunkKey, duration)) {
                    broadcastLockdownNotification(worldId, world.getName(), chunkKey, duration);
                }
            }
            applyPistonRemoval(block, worldId, blockKey);
            counterManager.handleRedstoneRemoval(block.getLocation(), block.getType(), chunkKey, blockKey);
        }
    }

    private boolean hasPlayersNearby(World world, int x, int y, int z) {
        int radius = configManager.getPluginConfig().getProximity().getPlayerRadius();
        int radiusSq = radius * radius;
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            if ((dx * dx + dy * dy + dz * dz) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private void broadcastLockdownNotification(UUID worldId, String worldName, long chunkKey, int duration) {
        int chunkX = (int) (chunkKey >> 32);
        int chunkZ = (int) chunkKey;

        final net.kyori.adventure.text.Component adminMsg =
                counterManager.getPlugin().getMessageManager().parseMessage(
                        counterManager.getPlugin().getMessageManager().getMessagesConfig().getAlerts().getChunkLockdownAdmin())
                        .replaceText(t -> t.matchLiteral("{world}").replacement(worldName))
                        .replaceText(t -> t.matchLiteral("{chunkX}").replacement(String.valueOf(chunkX)))
                        .replaceText(t -> t.matchLiteral("{chunkZ}").replacement(String.valueOf(chunkZ)))
                        .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration)));

        final net.kyori.adventure.text.Component localMsg =
                counterManager.getPlugin().getMessageManager().parseMessage(
                        counterManager.getPlugin().getMessageManager().getMessagesConfig().getAlerts().getChunkLockdownLocal())
                        .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(duration)));

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

        Location loc = block.getLocation();
        Runnable task = () -> {
            if (block.getType() == Material.AIR) return;
            if (cachedRemovalAction == ConfigManager.RemovalAction.REMOVE) {
                block.setType(Material.AIR, false);
            } else if (cachedRemovalAction == ConfigManager.RemovalAction.DROP) {
                block.breakNaturally();
            }
        };

        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().execute(counterManager.getPlugin(), loc, task);
        } else {
            Bukkit.getScheduler().runTask(counterManager.getPlugin(), task);
        }
    }

    private void applyPistonRemoval(Block block, UUID worldId, long blockKey) {
        disabledBlocks.computeIfAbsent(worldId, k -> new LongOpenHashSet()).add(blockKey);

        if (block.getType() == Material.AIR || cachedRemovalAction == ConfigManager.RemovalAction.DISABLE) {
            return;
        }

        Location loc = block.getLocation();
        Runnable task = () -> {
            if (block.getType() == Material.AIR) return;
            if (cachedRemovalAction == ConfigManager.RemovalAction.REMOVE) {
                block.setType(Material.AIR, false);
            } else if (cachedRemovalAction == ConfigManager.RemovalAction.DROP) {
                block.breakNaturally();
            }
        };

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

    public void cleanupChunk(Chunk chunk) {
        UUID worldId = chunk.getWorld().getUID();
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!cachedRedstoneMaterials.contains(block.getType())) return;

        UUID worldId = block.getWorld().getUID();
        long blockKey = CounterManager.packBlock(block.getX(), block.getY(), block.getZ());
        long chunkKey = CounterManager.packChunk(block.getX() >> 4, block.getZ() >> 4);

        if (counterManager.isBlockFrozen(worldId, blockKey)) {
            long remaining = counterManager.getFreezeRemaining(worldId, blockKey) / 1000;
            event.setCancelled(true);
            event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                    "§e[!] This redstone component is paused for " + remaining + "s due to high frequency."));
            return;
        }

        if (counterManager.isChunkLocked(worldId, chunkKey)) {
            long remaining = counterManager.getLockdownRemaining(worldId, chunkKey) / 1000;
            event.setCancelled(true);
            event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                    "§c[!] Redstone in this chunk is on lockdown for " + remaining + "s due to extreme lag."));
        }
    }
}