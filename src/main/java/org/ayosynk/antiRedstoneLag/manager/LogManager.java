package org.ayosynk.antiRedstoneLag.manager;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public class LogManager {
    /** Flush to disk after this many pending entries. */
    private static final int  BATCH_SIZE     = 50;
    /** Flush to disk at least every 500 ms regardless of batch size. */
    private static final long FLUSH_INTERVAL_MS = 500;
    /** Only check log rotation every N flushes to avoid repeated file-stat calls. */
    private static final int  ROTATION_CHECK_INTERVAL = 20;

    /**
     * Thread-safe, immutable {@link DateTimeFormatter} used for log-line timestamps.
     * Avoids allocating a {@code new Date(timestamp)} on every write that
     * {@link java.text.SimpleDateFormat} required.
     */
    private static final DateTimeFormatter LOG_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Thread-safe formatter used for log-file name date suffixes. */
    private static final DateTimeFormatter FILE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /** Thread-safe formatter used only during log rotation for the time-of-rotation suffix. */
    private static final DateTimeFormatter ROTATE_FORMATTER =
            DateTimeFormatter.ofPattern("HH-mm-ss").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final ConcurrentLinkedQueue<LogEntry> logQueue;
    private BufferedWriter logWriter;
    private boolean enabled;
    private boolean consoleMirror;
    private int maxLogFiles;
    private long maxLogSize;
    private File logsFolder;
    private volatile boolean running;
    private Thread logThread;
    private volatile long lastFlushTime;
    private volatile int  pendingWrites;
    /** Counts flushes so rotation is only checked every {@link #ROTATION_CHECK_INTERVAL} flushes. */
    private int flushCount = 0;

    private static class LogEntry {
        final long   timestamp;
        final String type;
        final String message;
        final String locationInfo;

        LogEntry(long timestamp, String type, String message, String locationInfo) {
            this.timestamp    = timestamp;
            this.type         = type;
            this.message      = message;
            this.locationInfo = locationInfo;
        }
    }

    public LogManager(JavaPlugin plugin) {
        this.plugin    = plugin;
        this.logQueue  = new ConcurrentLinkedQueue<>();
        this.running   = true;
        setupLogging();
        startLogThread();
    }

    private void setupLogging() {
        enabled       = plugin.getConfig().getBoolean("logging.enabled", true);
        consoleMirror = plugin.getConfig().getBoolean("logging.console-mirror", false);
        maxLogFiles   = plugin.getConfig().getInt("logging.max-files", 10);
        maxLogSize    = plugin.getConfig().getLong("logging.max-size-mb", 10) * 1024 * 1024;

        if (!enabled) return;

        logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }

        try {
            String currentDate = FILE_FORMATTER.format(Instant.now());
            File logFile = new File(logsFolder, "redstone-logs-" + currentDate + ".log");
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            logWriter = new BufferedWriter(new FileWriter(logFile, true));
            logToFile("SYSTEM", "Logging system initialized", null);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize log file!", e);
        }
    }

    private void startLogThread() {
        logThread = new Thread(() -> {
            while (running || !logQueue.isEmpty()) {
                try {
                    processLogQueue();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    processLogQueue();
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            processLogQueue(); // Final drain
        }, "AntiRedstoneLag-Logger");
        logThread.setDaemon(true);
        logThread.start();
    }

    private void processLogQueue() {
        if (logWriter == null) return;
        try {
            while (!logQueue.isEmpty()) {
                LogEntry entry = logQueue.poll();
                if (entry != null) {
                    // DateTimeFormatter.format(Instant) is thread-safe and avoids Date allocation.
                    String timestamp = LOG_FORMATTER.format(Instant.ofEpochMilli(entry.timestamp));
                    String logLine   = String.format("[%s] [%s] %s%s",
                            timestamp, entry.type, entry.message,
                            entry.locationInfo != null ? " | " + entry.locationInfo : "");
                    logWriter.write(logLine);
                    logWriter.newLine();
                    pendingWrites++;

                    if (consoleMirror) {
                        plugin.getLogger().info(logLine);
                    }
                }
            }

            // Batch flush: only flush if enough entries queued or enough time elapsed.
            long now = System.currentTimeMillis();
            if (pendingWrites >= BATCH_SIZE ||
                    (pendingWrites > 0 && now - lastFlushTime >= FLUSH_INTERVAL_MS)) {
                logWriter.flush();
                pendingWrites = 0;
                lastFlushTime = now;

                // Check log rotation only every ROTATION_CHECK_INTERVAL flushes
                // to avoid repeated file-stat I/O on every flush.
                if (++flushCount >= ROTATION_CHECK_INTERVAL) {
                    flushCount = 0;
                    checkLogRotation();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write to log file!", e);
        }
    }

    private void checkLogRotation() {
        if (logsFolder == null) return;
        String currentDate  = FILE_FORMATTER.format(Instant.now());
        File   currentLog   = new File(logsFolder, "redstone-logs-" + currentDate + ".log");
        if (currentLog.exists() && currentLog.length() > maxLogSize) {
            rotateLogFile(currentLog, currentDate);
        }
    }

    private void rotateLogFile(File currentLogFile, String currentDate) {
        try {
            if (logWriter != null) {
                logWriter.close();
            }
            String rotateTimestamp = ROTATE_FORMATTER.format(Instant.now());
            File   rotatedFile     = new File(logsFolder,
                    "redstone-logs-" + currentDate + "-" + rotateTimestamp + ".log");
            try {
                Files.move(currentLogFile.toPath(), rotatedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveEx) {
                if (!currentLogFile.renameTo(rotatedFile)) {
                    plugin.getLogger().warning("Failed to rotate log file: " + currentLogFile.getName());
                }
            }
            currentLogFile.createNewFile();
            logWriter = new BufferedWriter(new FileWriter(currentLogFile, true));
            logToFile("SYSTEM", "Log file rotated due to size limit", null);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to rotate log file!", e);
        }
    }

    public void logToFile(String type, String message, Location location) {
        if (!enabled) return;
        String locationInfo = null;
        if (location != null) {
            if (location.getWorld() == null) {
                locationInfo = String.format("Location: %s,%s,%s | World: unknown",
                        location.getBlockX(), location.getBlockY(), location.getBlockZ());
            } else {
                locationInfo = String.format("Location: %s,%s,%s | World: %s | Chunk: %s,%s",
                        location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                        location.getWorld().getName(),
                        location.getChunk().getX(), location.getChunk().getZ());
            }
        }
        logQueue.offer(new LogEntry(System.currentTimeMillis(), type, message, locationInfo));
    }

    public void logRedstoneRemoval(Location location, Material material,
                                    int chunkCount, int blockCount, String reason) {
        if (!enabled) return;
        String message = String.format(
                "Redstone removed | Material: %s | Chunk Updates: %d | Block Updates: %d | Reason: %s",
                material.toString(), chunkCount, blockCount, reason);
        logToFile("REDSTONE_REMOVED", message, location);
    }

    public void logPerformanceStats(int chunksMonitored, int blocksMonitored, double avgUpdatesPerSecond) {
        if (!enabled) return;
        String message = String.format("Performance Stats | Chunks: %d | Blocks: %d | Avg UPS: %.2f",
                chunksMonitored, blocksMonitored, avgUpdatesPerSecond);
        logToFile("PERFORMANCE", message, null);
    }

    public void cleanupOldLogs() {
        if (!enabled) return;
        File[] logFiles = logsFolder.listFiles(
                (dir, name) -> name.startsWith("redstone-logs-") && name.endsWith(".log"));
        if (logFiles != null && logFiles.length > maxLogFiles) {
            java.util.Arrays.sort(logFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            for (int i = 0; i < logFiles.length - maxLogFiles; i++) {
                if (logFiles[i].delete()) {
                    plugin.getLogger().info("Deleted old log file: " + logFiles[i].getName());
                }
            }
        }
        checkLogRotation();
    }

    public void close() {
        running = false;
        if (logThread != null) {
            logThread.interrupt();
            try {
                logThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (logWriter != null) {
            try {
                processLogQueue();
                logWriter.close();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to close log writer!", e);
            }
        }
    }

    public File    getLogsFolder() { return logsFolder; }
    public boolean isEnabled()     { return enabled; }
}