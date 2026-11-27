package org.ayosynk.antiRedstoneLag.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.bukkit.Bukkit;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final long EMERGENCY_CLEAR_COOLDOWN_MS = 30000L;

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private final AtomicInteger secondsUntilClear = new AtomicInteger(300);
    private volatile boolean timerRunning = false;
    private final AtomicBoolean clearingInProgress = new AtomicBoolean(false);
    private volatile long lastEmergencyClearTime = 0L;

    private volatile List<EntityFilterRule> cachedWhitelistRules = new ArrayList<>();
    private volatile List<EntityFilterRule> cachedRemoveRules = new ArrayList<>();

    public ClearLaggManager(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        reloadRules();
        startAutoClearTask();
    }

    public void reloadRules() {
        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        this.cachedWhitelistRules = parseRules(cfg.getEntityWhitelist(), "entity-whitelist");
        this.cachedRemoveRules = parseRules(cfg.getRemoveEntities(), "remove-entities");

        int interval = cfg.getIntervalSeconds();
        if (interval > 0 && secondsUntilClear.get() > interval) {
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
        if (timerRunning) return;
        timerRunning = true;

        int interval = configManager.getPluginConfig().getClearlagg().getIntervalSeconds();
        secondsUntilClear.set(interval > 0 ? interval : 300);

        plugin.getScheduler().runTaskTimer(() -> {
            PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
            if (!cfg.isEnabled() || cfg.getIntervalSeconds() <= 0) return;

            int remaining = secondsUntilClear.decrementAndGet();

            if (cfg.getCountdownSeconds().contains(remaining)) {
                broadcastCountdown(remaining);
            }

            if (remaining <= 0) {
                clearEntities(ClearType.AUTO);
                secondsUntilClear.set(cfg.getIntervalSeconds());
            }

            if (!CounterManager.isFolia() && cfg.getTpsEmergencyThreshold() > 0.0) {
                long now = System.currentTimeMillis();
                if (now - lastEmergencyClearTime >= EMERGENCY_CLEAR_COOLDOWN_MS) {
                    try {
                        double tps = Bukkit.getTPS()[0];
                        if (tps <= cfg.getTpsEmergencyThreshold()) {
                            lastEmergencyClearTime = now;
                            clearEntities(ClearType.AUTO);
                            secondsUntilClear.set(cfg.getIntervalSeconds());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, 20L, 20L);
    }

    public void broadcastCountdown(int seconds) {
        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        PluginConfig.BroadcastMode mode = cfg.getBroadcastMode();
        if (mode == PluginConfig.BroadcastMode.NONE) return;

        Component chatMsg = messageManager.parseMessage(
                messageManager.getMessagesConfig().getClearlagg().getWarningChat())
                .replaceText(t -> t.matchLiteral("{seconds}").replacement(String.valueOf(seconds)));

        Component actionbarMsg = messageManager.parseMessage(
                messageManager.getMessagesConfig().getClearlagg().getWarningActionbar())
                .replaceText(t -> t.matchLiteral("{seconds}").replacement(String.valueOf(seconds)));

        Component titleMsg = messageManager.parseMessage(
                messageManager.getMessagesConfig().getClearlagg().getWarningTitle())
                .replaceText(t -> t.matchLiteral("{seconds}").replacement(String.valueOf(seconds)));

        Component subtitleMsg = messageManager.parseMessage(
                messageManager.getMessagesConfig().getClearlagg().getWarningSubtitle())
                .replaceText(t -> t.matchLiteral("{seconds}").replacement(String.valueOf(seconds)));

        Title title = Title.title(titleMsg, subtitleMsg,
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(400)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            switch (mode) {
                case CHAT:
                    player.sendMessage(chatMsg);
                    break;
                case ACTION_BAR:
                    player.sendActionBar(actionbarMsg);
                    break;
                case TITLE:
                    player.showTitle(Title.title(titleMsg, Component.empty(),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(400))));
                    break;
                case SUBTITLE:
                    player.showTitle(Title.title(Component.empty(), subtitleMsg,
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(400))));
                    break;
                case ALL:
                    player.sendMessage(chatMsg);
                    player.sendActionBar(actionbarMsg);
                    player.showTitle(title);
                    break;
            }
        }
    }

    public int clearEntities(ClearType type) {
        if (!clearingInProgress.compareAndSet(false, true)) {
            return 0;
        }

        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        Set<Material> itemBlacklist = new HashSet<>(cfg.getItemBlacklist());
        List<EntityFilterRule> whitelist = cachedWhitelistRules;
        List<EntityFilterRule> removeRules = cachedRemoveRules;

        List<Entity> allEntities = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            allEntities.addAll(world.getEntities());
        }

        if (allEntities.isEmpty()) {
            clearingInProgress.set(false);
            broadcastCleared(0);
            return 0;
        }

        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger pending = new AtomicInteger(allEntities.size());
        AtomicBoolean broadcastSent = new AtomicBoolean(false);

        Runnable checkDone = () -> {
            if (pending.decrementAndGet() <= 0) {
                if (broadcastSent.compareAndSet(false, true)) {
                    clearingInProgress.set(false);
                    broadcastCleared(count.get());
                }
            }
        };

        // Safety fallback timeout after 2 seconds (40 ticks)
        plugin.getScheduler().runTaskLater(() -> {
            if (broadcastSent.compareAndSet(false, true)) {
                clearingInProgress.set(false);
                broadcastCleared(count.get());
            }
        }, 40L);

        for (Entity entity : allEntities) {
            if (entity == null) {
                checkDone.run();
                continue;
            }

            if (Bukkit.isOwnedByCurrentRegion(entity)) {
                try {
                    if (entity.isValid() && shouldRemove(entity, type, cfg, itemBlacklist, whitelist, removeRules)) {
                        entity.remove();
                        count.incrementAndGet();
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error processing entity for clear: " + t.getMessage());
                } finally {
                    checkDone.run();
                }
            } else {
                try {
                    boolean scheduled = entity.getScheduler().execute(plugin, () -> {
                        try {
                            if (entity.isValid() && shouldRemove(entity, type, cfg, itemBlacklist, whitelist, removeRules)) {
                                entity.remove();
                                count.incrementAndGet();
                            }
                        } catch (Throwable t) {
                            plugin.getLogger().warning("Error removing entity on region thread: " + t.getMessage());
                        } finally {
                            checkDone.run();
                        }
                    }, checkDone, 1L);

                    if (!scheduled) {
                        checkDone.run();
                    }
                } catch (Throwable t) {
                    checkDone.run();
                }
            }
        }

        return count.get();
    }

    private void broadcastCleared(int removed) {
        String raw = messageManager.getMessagesConfig().getClearlagg().getCleared();
        if (raw == null || raw.isBlank()) return;
        Component clearedMsg = messageManager.parseMessage(raw)
                .replaceText(t -> t.matchLiteral("{count}").replacement(String.valueOf(removed)));

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
        Component cancelMsg = messageManager.parseMessage(
                messageManager.getMessagesConfig().getClearlagg().getCancelled());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(cancelMsg);
        }
    }

    public int getSecondsUntilClear() {
        return Math.max(0, secondsUntilClear.get());
    }

    private boolean shouldRemove(Entity entity, ClearType type, PluginConfig.ClearLaggConfig cfg,
                                 Set<Material> itemBlacklist, List<EntityFilterRule> whitelist,
                                 List<EntityFilterRule> removeRules) {
        if (entity instanceof Player) return false;

        for (EntityFilterRule wRule : whitelist) {
            if (wRule.matches(entity)) {
                return false;
            }
        }

        if (entity instanceof Item item) {
            if (type == ClearType.MOBS || type == ClearType.PROJECTILES || type == ClearType.XP || type == ClearType.VEHICLES) return false;
            if (type == ClearType.ITEMS || type == ClearType.ALL || (type == ClearType.AUTO && cfg.isClearGroundItems())) {
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
