package org.ayosynk.antiRedstoneLag.storage;

import org.ayosynk.antiRedstoneLag.config.PluginConfig;

import java.io.File;
import java.util.logging.Logger;

/**
 * Selects and initializes the configured storage backend with safe fallback.
 */
public final class StorageFactory {

    private StorageFactory() {
    }

    public static StorageService create(File dataFolder, PluginConfig.StorageConfig config, Logger logger) {
        PluginConfig.StorageBackend requested = config != null ? config.getBackend() : PluginConfig.StorageBackend.AUTO;
        String sqlitePath = config != null ? config.getSqliteFile() : "data/antiredstonelag.db";

        if (requested == PluginConfig.StorageBackend.JSON) {
            JsonStorageService json = new JsonStorageService(dataFolder, logger);
            json.initialize();
            return json;
        }

        if (SqliteStorageService.isDriverAvailable()) {
            try {
                SqliteStorageService sqlite = new SqliteStorageService(dataFolder, sqlitePath, logger);
                sqlite.initialize();
                return sqlite;
            } catch (Exception e) {
                if (requested == PluginConfig.StorageBackend.SQLITE && logger != null) {
                    logger.warning("SQLite storage failed (" + e.getMessage() + "); falling back to JSON.");
                }
            }
        } else if (requested == PluginConfig.StorageBackend.SQLITE && logger != null) {
            logger.warning("SQLite JDBC driver not present; falling back to JSON storage.");
        }

        JsonStorageService fallback = new JsonStorageService(dataFolder, logger);
        fallback.initialize();
        return fallback;
    }
}
