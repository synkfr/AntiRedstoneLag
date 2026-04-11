package org.ayosynk.antiRedstoneLag.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class FoliaSchedulerImpl implements Scheduler {
    private final JavaPlugin plugin;

    public FoliaSchedulerImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runTaskTimer(Runnable task, long delay, long period) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delay, period);
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public void runTaskTimerAsynchronously(Runnable task, long delay, long period) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delay * 50, period * 50, TimeUnit.MILLISECONDS);
    }

    @Override
    public void runTaskLater(Runnable task, long delay) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delay);
    }

    @Override
    public void runTaskLater(Player player, Runnable task, long delay) {
        player.getScheduler().runDelayed(plugin, t -> task.run(), null, delay);
    }
}
