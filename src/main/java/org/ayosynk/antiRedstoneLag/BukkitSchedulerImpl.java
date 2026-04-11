package org.ayosynk.antiRedstoneLag;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitSchedulerImpl implements Scheduler {
    private final JavaPlugin plugin;

    public BukkitSchedulerImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runTaskTimer(Runnable task, long delay, long period) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runTaskTimerAsynchronously(Runnable task, long delay, long period) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
    }

    @Override
    public void runTaskLater(Runnable task, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    @Override
    public void runTaskLater(Player player, Runnable task, long delay) {
        // In standard Bukkit, we just run it on the main thread
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }
}
