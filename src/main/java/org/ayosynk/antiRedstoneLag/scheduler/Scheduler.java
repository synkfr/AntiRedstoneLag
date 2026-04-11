package org.ayosynk.antiRedstoneLag.scheduler;

import org.bukkit.entity.Player;

/**
 * Interface for scheduling tasks across different platforms (Bukkit/Folia).
 */
public interface Scheduler {
    /**
     * Run a task on the global region or main thread after a delay, then repeatedly.
     */
    void runTaskTimer(Runnable task, long delay, long period);

    /**
     * Run a task asynchronously.
     */
    void runTaskAsynchronously(Runnable task);

    /**
     * Run a task asynchronously after a delay, then repeatedly.
     */
    void runTaskTimerAsynchronously(Runnable task, long delay, long period);

    /**
     * Run a task later on the global region or main thread.
     */
    void runTaskLater(Runnable task, long delay);

    /**
     * Run a task later on the region thread of a specific player.
     */
    void runTaskLater(Player player, Runnable task, long delay);
}
