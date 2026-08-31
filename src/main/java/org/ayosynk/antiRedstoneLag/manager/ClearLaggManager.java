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

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private final AtomicInteger secondsUntilClear = new AtomicInteger(300);
    private volatile boolean timerRunning = false;

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
        List<EntityFilterRule> wList = new ArrayList<>();
        for (String ruleStr : cfg.getEntityWhitelist()) {
            EntityFilterRule r = EntityFilterRule.parse(ruleStr);
            if (r != null) wList.add(r);
        }
        this.cachedWhitelistRules = wList;

        List<EntityFilterRule> rList = new ArrayList<>();
        for (String ruleStr : cfg.getRemoveEntities()) {
            EntityFilterRule r = EntityFilterRule.parse(ruleStr);
            if (r != null) rList.add(r);
        }
        this.cachedRemoveRules = rList;
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

            if (cfg.getTpsEmergencyThreshold() > 0.0) {
                double tps = Bukkit.getTPS()[0];
                if (tps <= cfg.getTpsEmergencyThreshold()) {
                    clearEntities(ClearType.AUTO);
                    secondsUntilClear.set(cfg.getIntervalSeconds());
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
        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        Set<Material> itemBlacklist = new HashSet<>(cfg.getItemBlacklist());
        List<EntityFilterRule> whitelist = cachedWhitelistRules;
        List<EntityFilterRule> removeRules = cachedRemoveRules;

        AtomicInteger count = new AtomicInteger(0);

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (shouldRemove(entity, type, cfg, itemBlacklist, whitelist, removeRules)) {
                    removeEntitySafely(entity);
                    count.incrementAndGet();
                }
            }
        }

        int removed = count.get();
        Component clearedMsg = messageManager.parseMessage(
                messageManager.getMessagesConfig().getClearlagg().getCleared())
                .replaceText(t -> t.matchLiteral("{count}").replacement(String.valueOf(removed)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(clearedMsg);
        }
        if (configManager.isLogToConsole()) {
            Bukkit.getConsoleSender().sendMessage(clearedMsg);
        }

        return removed;
    }

    public EntityCounts countEntities() {
        EntityCounts counts = new EntityCounts();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
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

    private void removeEntitySafely(Entity entity) {
        try {
            Bukkit.getRegionScheduler().execute(plugin, entity.getLocation(), entity::remove);
        } catch (Throwable t) {
            entity.remove();
        }
    }
}
