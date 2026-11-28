package org.ayosynk.antiRedstoneLag;

import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTests {

    @Test
    void upgradesV1ConfigAndMessagesWithBackupsAndPreservesV1Settings(@TempDir Path tempDir) throws Exception {
        String v1Config = """
                # AntiRedstoneLag Configuration
                config-version: "26.5"
                chunk-threshold: 888
                block-threshold: 24
                reset-interval-ticks: 40
                redstone-enabled: true
                removal-action: NOTIFY
                enabled-worlds:
                  - "world"
                  - "world_nether"
                clearlagg:
                  enabled: true
                  interval-seconds: 180
                  clear-ground-items: false
                whitelist:
                  enabled: true
                  chunks:
                    - "world:12:-34"
                """;

        String v1Messages = """
                # AntiRedstoneLag Messages
                messages:
                  prefix: "&#4ECDC4[ARL]&r "
                  enabled: "&#4ECDC4✓ &aOld enabled message"
                """;

        File configFile = tempDir.resolve("config.yml").toFile();
        File messagesFile = tempDir.resolve("messages.yml").toFile();
        Files.writeString(configFile.toPath(), v1Config, StandardCharsets.UTF_8);
        Files.writeString(messagesFile.toPath(), v1Messages, StandardCharsets.UTF_8);

        Logger logger = Logger.getLogger("ConfigMigrationTests");
        ConfigManager cm = new ConfigManager(null, tempDir.toFile(), logger);
        MessageManager mm = new MessageManager(tempDir.toFile(), logger);

        File configBackup = tempDir.resolve("config.yml.bk").toFile();
        File messagesBackup = tempDir.resolve("messages.yml.bk").toFile();
        assertTrue(configBackup.exists(), "config.yml.bk should be created when upgrading from v1");
        assertTrue(messagesBackup.exists(), "messages.yml.bk should be created when upgrading from v1");
        assertEquals(v1Config, Files.readString(configBackup.toPath(), StandardCharsets.UTF_8));
        assertEquals(v1Messages, Files.readString(messagesBackup.toPath(), StandardCharsets.UTF_8));

        PluginConfig migrated = cm.getPluginConfig();
        assertEquals(2, migrated.getConfigVersion());
        assertEquals(888, migrated.getChunkThreshold());
        assertEquals(24, migrated.getBlockThreshold());
        assertEquals(40, migrated.getResetIntervalTicks());
        assertEquals(PluginConfig.RemovalAction.NOTIFY, migrated.getRemovalAction());
        assertFalse(migrated.getClearlagg().isClearGroundItems());
        assertEquals(180, migrated.getClearlagg().getIntervalSeconds());
        assertTrue(cm.isChunkWhitelisted("world", 12, -34));
        assertTrue(cm.isWorldEnabled("world_nether"));
        assertFalse(cm.isWorldEnabled("world_the_end"));

        assertEquals(2, mm.getMessagesConfig().getMessagesVersion());
        assertTrue(mm.getMessagesConfig().getMessages().getPrefix().contains("#00E262"));

        // Modifying .bk sentinel and reloading on v2 must NOT overwrite backups,
        // and Material lists / enums must round-trip cleanly from disk.
        Files.writeString(configBackup.toPath(), "sentinel-config-bk", StandardCharsets.UTF_8);
        Files.writeString(messagesBackup.toPath(), "sentinel-messages-bk", StandardCharsets.UTF_8);
        cm.reloadConfig();
        mm.reloadMessages();
        assertEquals("sentinel-config-bk", Files.readString(configBackup.toPath(), StandardCharsets.UTF_8));
        assertEquals("sentinel-messages-bk", Files.readString(messagesBackup.toPath(), StandardCharsets.UTF_8));
        assertTrue(cm.getRedstoneMaterials().contains(org.bukkit.Material.OBSERVER));
        assertTrue(cm.getRedstoneMaterials().contains(org.bukkit.Material.CRAFTER));
        assertTrue(cm.getPluginConfig().getClearlagg().getItemBlacklist().contains(org.bukkit.Material.ELYTRA));
        assertEquals(PluginConfig.RemovalAction.NOTIFY, cm.getRemovalAction("world"));
    }
}
