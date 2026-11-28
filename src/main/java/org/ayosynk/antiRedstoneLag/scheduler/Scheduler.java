package org.ayosynk.antiRedstoneLag.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Platform-neutral scheduler supporting Paper, Purpur, and Folia regional execution.
 */
public interface Scheduler {
    TaskHandle runTaskTimer(Runnable task, long delayTicks, long periodTicks);

    void runTaskAsynchronously(Runnable task);

    TaskHandle runTaskTimerAsynchronously(Runnable task, long delayTicks, long periodTicks);

    TaskHandle runTaskLater(Runnable task, long delayTicks);

    TaskHandle runTaskLater(Player player, Runnable task, long delayTicks);

    void runTask(Player player, Runnable task);

    void runAtLocation(Location location, Runnable task);

    boolean runForEntity(Entity entity, Runnable task, Runnable retired);
}
