package org.ayosynk.antiRedstoneLag.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Paper/Folia regional, entity, global, and async scheduler implementation.
 */
public class FoliaSchedulerImpl implements Scheduler {
    private final JavaPlugin plugin;

    public FoliaSchedulerImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask st = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, t -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return wrap(st);
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public TaskHandle runTaskTimerAsynchronously(Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask st = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, t -> task.run(),
                Math.max(50L, delayTicks * 50L),
                Math.max(50L, periodTicks * 50L),
                TimeUnit.MILLISECONDS);
        return wrap(st);
    }

    @Override
    public TaskHandle runTaskLater(Runnable task, long delayTicks) {
        ScheduledTask st = Bukkit.getGlobalRegionScheduler().runDelayed(
                plugin, t -> task.run(), Math.max(1L, delayTicks));
        return wrap(st);
    }

    @Override
    public TaskHandle runTaskLater(Player player, Runnable task, long delayTicks) {
        ScheduledTask st = player.getScheduler().runDelayed(
                plugin, t -> task.run(), null, Math.max(1L, delayTicks));
        return wrap(st);
    }

    @Override
    public void runTask(Player player, Runnable task) {
        player.getScheduler().run(plugin, t -> task.run(), null);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    @Override
    public boolean runForEntity(Entity entity, Runnable task, Runnable retired) {
        if (entity == null) {
            if (retired != null) retired.run();
            return false;
        }
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            if (entity.isValid()) {
                task.run();
            } else if (retired != null) {
                retired.run();
            }
            return true;
        }
        boolean scheduled = entity.getScheduler().execute(plugin, task, retired, 1L);
        if (!scheduled && retired != null) {
            retired.run();
        }
        return scheduled;
    }

    private static TaskHandle wrap(ScheduledTask task) {
        if (task == null) {
            return new TaskHandle() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return true;
                }
            };
        }
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
