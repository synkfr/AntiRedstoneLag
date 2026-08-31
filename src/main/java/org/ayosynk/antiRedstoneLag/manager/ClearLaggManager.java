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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;

import java.time.Duration;
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

    public ClearLaggManager(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        startAutoClearTask();
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
        Set<EntityType> entityWhitelist = new HashSet<>(cfg.getEntityWhitelist());

        AtomicInteger count = new AtomicInteger(0);

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (shouldRemove(entity, type, cfg, itemBlacklist, entityWhitelist)) {
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
                                 Set<Material> itemBlacklist, Set<EntityType> entityWhitelist) {
        if (entity instanceof Player) return false;
        if (entityWhitelist.contains(entity.getType())) return false;
        if (entity instanceof ArmorStand || entity instanceof ItemFrame || entity instanceof Painting) return false;

        if (entity instanceof Item item) {
            if (type == ClearType.MOBS || type == ClearType.PROJECTILES || type == ClearType.XP || type == ClearType.VEHICLES) return false;
            if (type == ClearType.ITEMS || type == ClearType.ALL || (type == ClearType.AUTO && cfg.isClearGroundItems())) {
                Material mat = item.getItemStack().getType();
                return !itemBlacklist.contains(mat);
            }
            return false;
        }

        if (entity instanceof Projectile) {
            if (type == ClearType.ITEMS || type == ClearType.MOBS || type == ClearType.XP || type == ClearType.VEHICLES) return false;
            if (type == ClearType.PROJECTILES || type == ClearType.ALL || (type == ClearType.AUTO && cfg.isClearProjectiles())) {
                if (entity instanceof AbstractArrow arrow) {
                    return arrow.isInBlock();
                }
                return true;
            }
            return false;
        }

        if (entity instanceof Monster) {
            if (type == ClearType.ITEMS || type == ClearType.PROJECTILES || type == ClearType.XP || type == ClearType.VEHICLES) return false;
            if (type == ClearType.MOBS || type == ClearType.ALL || (type == ClearType.AUTO && cfg.isClearUnnamedMonsters())) {
                return isMobEligible((LivingEntity) entity, cfg);
            }
            return false;
        }

        if (entity instanceof ExperienceOrb) {
            if (type == ClearType.XP || type == ClearType.ALL || (type == ClearType.AUTO && cfg.isClearXpOrbs())) {
                return true;
            }
            return false;
        }

        if (entity instanceof Vehicle) {
            if (type == ClearType.VEHICLES || type == ClearType.ALL || (type == ClearType.AUTO && cfg.isClearBoatsMinecarts())) {
                return entity.getPassengers().isEmpty();
            }
            return false;
        }

        return false;
    }

    private boolean isMobEligible(LivingEntity mob, PluginConfig.ClearLaggConfig cfg) {
        if (cfg.isExemptNamedMobs() && mob.customName() != null) return false;
        if (cfg.isExemptLeashedMobs() && mob.isLeashed()) return false;
        if (cfg.isExemptTamedAnimals() && mob instanceof Tameable tameable && tameable.isTamed()) return false;
        return true;
    }

    private void removeEntitySafely(Entity entity) {
        try {
            Bukkit.getRegionScheduler().execute(plugin, entity.getLocation(), entity::remove);
        } catch (Throwable t) {
            entity.remove();
        }
    }
}
