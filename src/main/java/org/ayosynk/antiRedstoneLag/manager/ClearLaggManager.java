package org.ayosynk.antiRedstoneLag.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.engine.AdaptiveMonitor;
import org.ayosynk.antiRedstoneLag.scheduler.TaskHandle;
import org.ayosynk.antiRedstoneLag.storage.PluginStats;
import org.ayosynk.antiRedstoneLag.storage.StorageService;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled, emergency, and manual ClearLagg entity cleaner with async completion and SMP-safe filters.
 */
public class ClearLaggManager {

    public enum ClearType {
        AUTO,
        ALL,
        ITEMS,
        PROJECTILES,
        MOBS,
        XP,
        VEHICLES
    }

    public static class EntityCounts {
        public int items;
        public int projectiles;
        public int monsters;
        public int xpOrbs;
        public int vehicles;

        public int getTotal() {
            return items + projectiles + monsters + xpOrbs + vehicles;
        }
    }

    public record ClearResult(int totalRemoved, int itemsRemoved, int mobsRemoved, int projectilesRemoved,
                              int xpRemoved, int vehiclesRemoved, int otherRemoved) {
    }

    private static final long EMERGENCY_CLEAR_COOLDOWN_MS = 30_000L;

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final StorageService storageService;
    private final PluginStats stats;

    private final AtomicInteger secondsUntilClear = new AtomicInteger(300);
    private final AtomicBoolean clearingInProgress = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile long lastEmergencyClearTime = 0L;
    private TaskHandle autoTaskHandle;

    private volatile List<EntityFilterRule> cachedWhitelistRules = List.of();
    private volatile List<EntityFilterRule> cachedRemoveRules = List.of();

    public ClearLaggManager(AntiRedstoneLag plugin,
                            ConfigManager configManager,
                            MessageManager messageManager,
                            StorageService storageService,
                            PluginStats stats) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.storageService = storageService;
        this.stats = stats;
        reloadRules();
        startAutoClearTask();
    }

    public void reloadRules() {
        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        this.cachedWhitelistRules = parseRules(cfg.getEntityWhitelist(), "entity-whitelist");
        this.cachedRemoveRules = parseRules(cfg.getRemoveEntities(), "remove-entities");

        int interval = cfg.getIntervalSeconds();
        if (interval > 0 && (secondsUntilClear.get() > interval || secondsUntilClear.get() <= 0)) {
            secondsUntilClear.set(interval);
        }
    }

    private List<EntityFilterRule> parseRules(List<String> rawRules, String sectionName) {
        List<EntityFilterRule> parsed = new ArrayList<>();
        if (rawRules == null) return parsed;
        for (String ruleStr : rawRules) {
            EntityFilterRule rule = EntityFilterRule.parse(ruleStr);
            if (rule == null) continue;
            if (!rule.isValid()) {
                plugin.getLogger().warning("Ignoring unknown ClearLagg entity rule in " + sectionName + ": " + ruleStr);
                continue;
            }
            parsed.add(rule);
        }
        return parsed;
    }

    public void startAutoClearTask() {
        if (autoTaskHandle != null && !autoTaskHandle.isCancelled()) {
            return;
        }

        int interval = configManager.getPluginConfig().getClearlagg().getIntervalSeconds();
        secondsUntilClear.set(interval > 0 ? interval : 300);

        autoTaskHandle = plugin.getScheduler().runTaskTimer(() -> {
            PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
            if (!cfg.isEnabled() || cfg.getIntervalSeconds() <= 0 || paused.get()) return;

            int remaining = secondsUntilClear.decrementAndGet();

            if (cfg.getCountdownSeconds().contains(remaining)) {
                broadcastCountdown(remaining);
            }

            if (remaining <= 0) {
                clearEntitiesAsync(ClearType.AUTO);
                secondsUntilClear.set(cfg.getIntervalSeconds());
            }

            if (!AdaptiveMonitor.isFolia() && cfg.getTpsEmergencyThreshold() > 0.0) {
                long now = System.currentTimeMillis();
                if (now - lastEmergencyClearTime >= EMERGENCY_CLEAR_COOLDOWN_MS) {
                    try {
                        double tps = Bukkit.getTPS()[0];
                        if (tps <= cfg.getTpsEmergencyThreshold()) {
                            lastEmergencyClearTime = now;
                            clearEntitiesAsync(ClearType.AUTO);
                            secondsUntilClear.set(cfg.getIntervalSeconds());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (autoTaskHandle != null) {
            autoTaskHandle.cancel();
            autoTaskHandle = null;
        }
    }

    public void broadcastCountdown(int seconds) {
        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        PluginConfig.BroadcastMode mode = cfg.getBroadcastMode();
        if (mode == PluginConfig.BroadcastMode.NONE) return;

        var clMsgs = messageManager.getMessagesConfig().getClearlagg();
        String rawChat = clMsgs.getWarningChat();
        String rawAction = clMsgs.getWarningActionbar();
        String rawTitle = clMsgs.getWarningTitle();
        String rawSub = clMsgs.getWarningSubtitle();

        Map<String, String> vars = Map.of("seconds", String.valueOf(seconds));
        Component chatMsg = (rawChat != null && !rawChat.isBlank()) ? messageManager.parseMessage(rawChat, vars) : null;
        Component actionbarMsg = (rawAction != null && !rawAction.isBlank()) ? messageManager.parseMessage(rawAction, vars) : null;
        Component titleMsg = (rawTitle != null && !rawTitle.isBlank()) ? messageManager.parseMessage(rawTitle, vars) : Component.empty();
        Component subtitleMsg = (rawSub != null && !rawSub.isBlank()) ? messageManager.parseMessage(rawSub, vars) : Component.empty();
        boolean hasTitle = (rawTitle != null && !rawTitle.isBlank()) || (rawSub != null && !rawSub.isBlank());

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(400));

        for (Player player : Bukkit.getOnlinePlayers()) {
            switch (mode) {
                case CHAT -> {
                    if (chatMsg != null) player.sendMessage(chatMsg);
                }
                case ACTION_BAR -> {
                    if (actionbarMsg != null) player.sendActionBar(actionbarMsg);
                }
                case TITLE -> {
                    if (rawTitle != null && !rawTitle.isBlank()) {
                        player.showTitle(Title.title(titleMsg, Component.empty(), times));
                    }
                }
                case SUBTITLE -> {
                    if (rawSub != null && !rawSub.isBlank()) {
                        player.showTitle(Title.title(Component.empty(), subtitleMsg, times));
                    }
                }
                case ALL -> {
                    if (chatMsg != null) player.sendMessage(chatMsg);
                    if (actionbarMsg != null) player.sendActionBar(actionbarMsg);
                    if (hasTitle) player.showTitle(Title.title(titleMsg, subtitleMsg, times));
                }
                case NONE -> {
                }
            }
        }
    }

    public CompletableFuture<ClearResult> clearEntitiesAsync(ClearType type) {
        CompletableFuture<ClearResult> future = new CompletableFuture<>();
        if (!clearingInProgress.compareAndSet(false, true)) {
            future.complete(new ClearResult(0, 0, 0, 0, 0, 0, 0));
            return future;
        }

        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        Set<Material> itemBlacklist = new HashSet<>(cfg.getItemBlacklist());
        List<EntityFilterRule> whitelist = cachedWhitelistRules;
        List<EntityFilterRule> removeRules = cachedRemoveRules;
        int minItemAgeTicks = cfg.getMinItemAgeSeconds() * 20;

        List<Entity> allEntities = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!configManager.isClearLaggEnabledInWorld(world.getName())) continue;
            allEntities.addAll(world.getEntities());
        }

        if (allEntities.isEmpty()) {
            clearingInProgress.set(false);
            ClearResult empty = new ClearResult(0, 0, 0, 0, 0, 0, 0);
            broadcastCleared(0);
            future.complete(empty);
            return future;
        }

        // Precompute optional per-chunk density caps if enabled.
        Set<Entity> capExceededEntities = computeChunkCapExcess(allEntities, cfg, itemBlacklist, whitelist);

        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger itemsCount = new AtomicInteger(0);
        AtomicInteger mobsCount = new AtomicInteger(0);
        AtomicInteger projCount = new AtomicInteger(0);
        AtomicInteger xpCount = new AtomicInteger(0);
        AtomicInteger vehicleCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        AtomicInteger pending = new AtomicInteger(allEntities.size());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable finish = () -> {
            if (completed.compareAndSet(false, true)) {
                clearingInProgress.set(false);
                int removed = total.get();
                if (removed > 0) {
                    stats.addEntitiesCleared(removed);
                    storageService.saveStatsAsync(stats);
                }
                broadcastCleared(removed);
                future.complete(new ClearResult(
                        removed, itemsCount.get(), mobsCount.get(),
                        projCount.get(), xpCount.get(), vehicleCount.get(), otherCount.get()
                ));
            }
        };

        Runnable checkDone = () -> {
            if (pending.decrementAndGet() <= 0) {
                finish.run();
            }
        };

        plugin.getScheduler().runTaskLater(finish, 40L);

        for (Entity entity : allEntities) {
            if (entity == null) {
                checkDone.run();
                continue;
            }
            boolean overCap = capExceededEntities.contains(entity);
            Runnable work = () -> {
                try {
                    if (entity.isValid() && (overCap || shouldRemove(entity, type, cfg, itemBlacklist, whitelist, removeRules, minItemAgeTicks))) {
                        recordCategory(entity, itemsCount, mobsCount, projCount, xpCount, vehicleCount, otherCount);
                        entity.remove();
                        total.incrementAndGet();
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error processing entity for clear: " + t.getMessage());
                } finally {
                    checkDone.run();
                }
            };
            plugin.getScheduler().runForEntity(entity, work, checkDone);
        }

        return future;
    }

    private Set<Entity> computeChunkCapExcess(List<Entity> entities, PluginConfig.ClearLaggConfig cfg,
                                              Set<Material> itemBlacklist, List<EntityFilterRule> whitelist) {
        var caps = cfg.getChunkEntityCaps();
        if (!caps.isEnabled()) return Set.of();

        Set<Entity> excess = new HashSet<>();
        Map<String, Integer> mobCounts = new HashMap<>();
        Map<String, Integer> itemCounts = new HashMap<>();

        for (Entity e : entities) {
            if (e == null || e instanceof Player || isWhitelisted(e, whitelist)) continue;
            Chunk c = e.getLocation().getChunk();
            String key = e.getWorld().getUID() + ":" + BlockKeyCodec.packChunk(c.getX(), c.getZ());
            if (e instanceof Item item) {
                if (itemBlacklist.contains(item.getItemStack().getType())) continue;
                int count = itemCounts.merge(key, 1, Integer::sum);
                if (count > caps.getMaxItemsPerChunk()) {
                    excess.add(e);
                }
            } else if (e instanceof Monster) {
                int count = mobCounts.merge(key, 1, Integer::sum);
                if (count > caps.getMaxMobsPerChunk()) {
                    excess.add(e);
                }
            }
        }
        return excess;
    }

    private static void recordCategory(Entity entity, AtomicInteger items, AtomicInteger mobs,
                                       AtomicInteger proj, AtomicInteger xp, AtomicInteger vehicles, AtomicInteger other) {
        if (entity instanceof Item) items.incrementAndGet();
        else if (entity instanceof Monster) mobs.incrementAndGet();
        else if (entity instanceof Projectile) proj.incrementAndGet();
        else if (entity instanceof ExperienceOrb) xp.incrementAndGet();
        else if (entity instanceof Vehicle) vehicles.incrementAndGet();
        else other.incrementAndGet();
    }

    private void broadcastCleared(int removed) {
        String raw = messageManager.getMessagesConfig().getClearlagg().getCleared();
        if (raw == null || raw.isBlank()) return;
        Component clearedMsg = messageManager.parseMessage(raw, Map.of("count", String.valueOf(removed)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(clearedMsg);
        }
        if (configManager.isLogToConsole()) {
            Bukkit.getConsoleSender().sendMessage(clearedMsg);
        }
    }

    public EntityCounts countEntities() {
        EntityCounts counts = new EntityCounts();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity == null) continue;
                if (entity instanceof Item) counts.items++;
                else if (entity instanceof Projectile) counts.projectiles++;
                else if (entity instanceof Monster) counts.monsters++;
                else if (entity instanceof ExperienceOrb) counts.xpOrbs++;
                else if (entity instanceof Vehicle) counts.vehicles++;
            }
        }
        return counts;
    }

    public void cancelScheduledClear() {
        int interval = configManager.getPluginConfig().getClearlagg().getIntervalSeconds();
        secondsUntilClear.set(interval > 0 ? interval : 300);
        String raw = messageManager.getMessagesConfig().getClearlagg().getCancelled();
        if (raw == null || raw.isBlank()) return;
        Component cancelMsg = messageManager.parseMessage(raw);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(cancelMsg);
        }
    }

    public boolean pauseTimer() {
        return paused.compareAndSet(false, true);
    }

    public boolean resumeTimer() {
        return paused.compareAndSet(true, false);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public int getSecondsUntilClear() {
        return Math.max(0, secondsUntilClear.get());
    }

    private boolean isWhitelisted(Entity entity, List<EntityFilterRule> whitelist) {
        for (EntityFilterRule wRule : whitelist) {
            if (wRule.matches(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRemove(Entity entity, ClearType type, PluginConfig.ClearLaggConfig cfg,
                                 Set<Material> itemBlacklist, List<EntityFilterRule> whitelist,
                                 List<EntityFilterRule> removeRules, int minItemAgeTicks) {
        if (entity instanceof Player) return false;
        if (isWhitelisted(entity, whitelist)) return false;

        if (entity instanceof Item item) {
            if (type == ClearType.MOBS || type == ClearType.PROJECTILES || type == ClearType.XP || type == ClearType.VEHICLES) {
                return false;
            }
            if (type == ClearType.ITEMS || type == ClearType.ALL || (type == ClearType.AUTO && cfg.isClearGroundItems())) {
                if (minItemAgeTicks > 0 && type == ClearType.AUTO && item.getTicksLived() < minItemAgeTicks) {
                    return false;
                }
                Material mat = item.getItemStack().getType();
                return !itemBlacklist.contains(mat);
            }
            return false;
        }

        if (type == ClearType.AUTO || type == ClearType.ALL) {
            for (EntityFilterRule rRule : removeRules) {
                if (rRule.matches(entity)) {
                    return true;
                }
            }
        } else if (type == ClearType.MOBS && entity instanceof Monster) {
            return true;
        } else if (type == ClearType.PROJECTILES && entity instanceof Projectile) {
            return true;
        } else if (type == ClearType.XP && entity instanceof ExperienceOrb) {
            return true;
        } else if (type == ClearType.VEHICLES && entity instanceof Vehicle) {
            return entity.getPassengers().isEmpty();
        }

        return false;
    }
}
