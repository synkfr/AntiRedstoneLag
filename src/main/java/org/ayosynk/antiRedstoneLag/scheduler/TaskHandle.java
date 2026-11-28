package org.ayosynk.antiRedstoneLag.scheduler;

/**
 * Handle for a scheduled task that supports cancellation across Bukkit and Folia.
 */
public interface TaskHandle {
    void cancel();

    boolean isCancelled();
}
