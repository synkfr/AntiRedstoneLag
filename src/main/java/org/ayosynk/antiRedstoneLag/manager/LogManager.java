package org.ayosynk.antiRedstoneLag.manager;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public class LogManager {
    private static final int BATCH_SIZE = 50;
    private static final long FLUSH_INTERVAL_MS = 500;
    private static final int ROTATION_CHECK_INTERVAL = 20;

    private static final DateTimeFormatter LOG_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter FILE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter ROTATE_FORMATTER =
            DateTimeFormatter.ofPattern("HH-mm-ss").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final ConcurrentLinkedQueue<LogEntry> logQueue;
    private final Object writerLock = new Object();
    private BufferedWriter logWriter;
    private String currentLogDate;
    private boolean enabled;
    private boolean consoleMirror;
    private int maxLogFiles;
    private long maxLogSize;
    private File logsFolder;
    private volatile boolean running;
    private Thread logThread;
    private volatile long lastFlushTime;
    private volatile int pendingWrites;
    private int flushCount = 0;

    private static class LogEntry {
        final long timestamp;
        final String type;
        final String message;
        final String locationInfo;

        LogEntry(long timestamp, String type, String message, String locationInfo) {
            this.timestamp = timestamp;
            this.type = type;
            this.message = message;
            this.locationInfo = locationInfo;
        }
    }

    public LogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.running = true;
        setupLogging();
        if (enabled) {
            startLogThread();
        }
    }

    private void setupLogging() {
        if (plugin instanceof AntiRedstoneLag arl && arl.getConfigManager() != null) {
            var logging = arl.getConfigManager().getPluginConfig().getLogging();
            enabled = logging.isEnabled();
            consoleMirror = logging.isConsoleMirror();
            maxLogFiles = logging.getMaxFiles();
            maxLogSize = logging.getMaxSizeMb() * 1024L * 1024L;
        } else {
            enabled = true;
            consoleMirror = false;
            maxLogFiles = 10;
            maxLogSize = 10L * 1024L * 1024L;
        }

        logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!enabled) return;

        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }

        try {
            currentLogDate = FILE_FORMATTER.format(Instant.now());
            File logFile = new File(logsFolder, "redstone-logs-" + currentLogDate + ".log");
            logWriter = Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
            processLogQueue();
        }, "AntiRedstoneLag-Logger");
        logThread.setDaemon(true);
        logThread.start();
    }

    private void processLogQueue() {
        synchronized (writerLock) {
            if (logWriter == null) return;
            try {
                while (!logQueue.isEmpty()) {
                    LogEntry entry = logQueue.poll();
                    if (entry != null) {
                        String timestamp = LOG_FORMATTER.format(Instant.ofEpochMilli(entry.timestamp));
                        String logLine = String.format("[%s] [%s] %s%s",
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

                long now = System.currentTimeMillis();
                if (pendingWrites >= BATCH_SIZE ||
                        (pendingWrites > 0 && now - lastFlushTime >= FLUSH_INTERVAL_MS)) {
                    logWriter.flush();
                    pendingWrites = 0;
                    lastFlushTime = now;

                    if (++flushCount >= ROTATION_CHECK_INTERVAL) {
                        flushCount = 0;
                        checkLogRotation();
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to write to log file!", e);
            }
        }
    }

    private void checkLogRotation() {
        if (logsFolder == null) return;
        String today = FILE_FORMATTER.format(Instant.now());
        if (!today.equals(currentLogDate)) {
            openDailyLogFile(today);
            return;
        }
        File currentLog = new File(logsFolder, "redstone-logs-" + currentLogDate + ".log");
        if (currentLog.exists() && currentLog.length() > maxLogSize) {
            rotateLogFile(currentLog, currentLogDate);
        }
    }

    private void openDailyLogFile(String newDate) {
        synchronized (writerLock) {
            try {
                if (logWriter != null) {
                    logWriter.flush();
                    logWriter.close();
                }
                currentLogDate = newDate;
                File newFile = new File(logsFolder, "redstone-logs-" + newDate + ".log");
                logWriter = Files.newBufferedWriter(newFile.toPath(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to open daily log file!", e);
            }
        }
    }

    private void rotateLogFile(File currentLogFile, String date) {
        synchronized (writerLock) {
            try {
                if (logWriter != null) {
                    logWriter.close();
                }
                String rotateTimestamp = ROTATE_FORMATTER.format(Instant.now());
                File rotatedFile = new File(logsFolder,
                        "redstone-logs-" + date + "-" + rotateTimestamp + ".log");
                try {
                    Files.move(currentLogFile.toPath(), rotatedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveEx) {
                    if (!currentLogFile.renameTo(rotatedFile)) {
                        plugin.getLogger().warning("Failed to rotate log file: " + currentLogFile.getName());
                    }
                }
                logWriter = Files.newBufferedWriter(currentLogFile.toPath(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                logToFile("SYSTEM", "Log file rotated due to size limit", null);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rotate log file!", e);
            }
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
                        location.getBlockX() >> 4, location.getBlockZ() >> 4);
            }
        }
        logQueue.offer(new LogEntry(System.currentTimeMillis(), type, message, locationInfo));
    }

    public void logRedstoneRemoval(Location location, Material material,
                                    int chunkCount, int blockCount, String reason) {
        if (!enabled) return;
        String message = String.format(
                "Redstone action | Material: %s | Chunk Updates: %d | Block Updates: %d | Reason: %s",
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
        if (!enabled || logsFolder == null) return;
        File[] logFiles = logsFolder.listFiles(
                (dir, name) -> name.startsWith("redstone-logs-") && name.endsWith(".log"));
        if (logFiles != null && logFiles.length > maxLogFiles) {
            // Sort oldest first so we delete only the oldest excess files.
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));
            int toDelete = logFiles.length - maxLogFiles;
            for (int i = 0; i < toDelete; i++) {
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
        synchronized (writerLock) {
            if (logWriter != null) {
                try {
                    processLogQueue();
                    logWriter.flush();
                    logWriter.close();
                    logWriter = null;
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to close log writer!", e);
                }
            }
        }
    }

    public File getLogsFolder() {
        return logsFolder;
    }

    public boolean isEnabled() {
        return enabled;
    }
}