package org.ayosynk.antiRedstoneLag.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bukkit/Paper main-thread scheduler implementation.
 */
public class BukkitSchedulerImpl implements Scheduler {
    private final JavaPlugin plugin;

    public BukkitSchedulerImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bt = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(0L, delayTicks), Math.max(1L, periodTicks));
        return wrap(bt);
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public TaskHandle runTaskTimerAsynchronously(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bt = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, Math.max(0L, delayTicks), Math.max(1L, periodTicks));
        return wrap(bt);
    }

    @Override
    public TaskHandle runTaskLater(Runnable task, long delayTicks) {
        BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
        return wrap(bt);
    }

    @Override
    public TaskHandle runTaskLater(Player player, Runnable task, long delayTicks) {
        BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, Math.max(1L, delayTicks));
        return wrap(bt);
    }

    @Override
    public void runTask(Player player, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            if (player.isOnline()) {
                task.run();
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                task.run();
            }
        });
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public boolean runForEntity(Entity entity, Runnable task, Runnable retired) {
        if (Bukkit.isPrimaryThread()) {
            if (entity.isValid()) {
                task.run();
            } else if (retired != null) {
                retired.run();
            }
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) {
                task.run();
            } else if (retired != null) {
                retired.run();
            }
        });
        return true;
    }

    private static TaskHandle wrap(BukkitTask task) {
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
