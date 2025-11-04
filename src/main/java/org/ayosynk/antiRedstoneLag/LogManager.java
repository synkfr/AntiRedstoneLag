package org.ayosynk.antiRedstoneLag;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public class LogManager {
    private final JavaPlugin plugin;
    private final ConcurrentLinkedQueue<String> logQueue;
    private BufferedWriter logWriter;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat fileDateFormat;
    private boolean enabled;
    private boolean consoleMirror;
    private int maxLogFiles;
    private long maxLogSize;
    private File logsFolder;
    private volatile boolean running;
    private Thread logThread;

    public LogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.fileDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        this.running = true;

        setupLogging();
        startLogThread();
    }

    private void setupLogging() {
        enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        consoleMirror = plugin.getConfig().getBoolean("logging.console-mirror", false);
        maxLogFiles = plugin.getConfig().getInt("logging.max-files", 10);
        maxLogSize = plugin.getConfig().getLong("logging.max-size-mb", 10) * 1024 * 1024;

        if (!enabled) return;

        logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }

        try {
            String currentDate = fileDateFormat.format(new Date());
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
                    Thread.sleep(100); // Process every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "AntiRedstoneLag-Logger");
        logThread.setDaemon(true);
        logThread.start();
    }

    private void processLogQueue() {
        if (logWriter == null) return;

        try {
            while (!logQueue.isEmpty()) {
                String logEntry = logQueue.poll();
                if (logEntry != null) {
                    logWriter.write(logEntry);
                    logWriter.newLine();
                    logWriter.flush();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write to log file!", e);
        }
    }

    public void logToFile(String type, String message, Location location) {
        if (!enabled) return;

        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("[%s] [%s] %s", timestamp, type, message);

        if (location != null) {
            logEntry += String.format(" | Location: %s,%s,%s | World: %s | Chunk: %s,%s",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                    location.getWorld().getName(),
                    location.getChunk().getX(), location.getChunk().getZ());
        }

        logQueue.offer(logEntry);
        if (consoleMirror) {
            plugin.getLogger().info(net.md_5.bungee.api.ChatColor.stripColor(logEntry));
        }
    }

    public void logRedstoneRemoval(Location location, Material material, int chunkCount, int blockCount, String reason) {
        if (!enabled) return;

        String message = String.format("Redstone removed | Material: %s | Chunk Updates: %d | Block Updates: %d | Reason: %s",
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

        File[] logFiles = logsFolder.listFiles((dir, name) -> name.startsWith("redstone-logs-") && name.endsWith(".log"));

        if (logFiles != null && logFiles.length > maxLogFiles) {
            // Sort by last modified
            java.util.Arrays.sort(logFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

            // Delete oldest files
            for (int i = 0; i < logFiles.length - maxLogFiles; i++) {
                if (logFiles[i].delete()) {
                    plugin.getLogger().info("Deleted old log file: " + logFiles[i].getName());
                }
            }
        }

        // Check current log file size
        String currentDate = fileDateFormat.format(new Date());
        File currentLogFile = new File(logsFolder, "redstone-logs-" + currentDate + ".log");

        if (currentLogFile.exists() && currentLogFile.length() > maxLogSize) {
            // Rotate log file
            String timestamp = new SimpleDateFormat("HH-mm-ss").format(new Date());
            File rotatedFile = new File(logsFolder, "redstone-logs-" + currentDate + "-" + timestamp + ".log");
            currentLogFile.renameTo(rotatedFile);

            try {
                currentLogFile.createNewFile();
                logWriter = new BufferedWriter(new FileWriter(currentLogFile, true));
                logToFile("SYSTEM", "Log file rotated due to size limit", null);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rotate log file!", e);
            }
        }
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
                processLogQueue(); // Process any remaining logs
                logWriter.close();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to close log writer!", e);
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