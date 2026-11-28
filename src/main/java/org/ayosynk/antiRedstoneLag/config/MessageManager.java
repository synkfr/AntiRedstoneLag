package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads messages.yml, backs up v1 files to messages.yml.bk, and parses design.md MiniMessage templates.
 */
public class MessageManager {
    public static final int CURRENT_MESSAGES_VERSION = 2;

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .extractUrls()
            .build();

    private final File dataFolder;
    private final Logger logger;
    private MessagesConfig messagesConfig;

    public MessageManager(JavaPlugin plugin) {
        this(plugin.getDataFolder(), plugin.getLogger());
    }

    public MessageManager(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        reloadMessages();
    }

    public void reloadMessages() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File messagesFile = new File(dataFolder, "messages.yml");
        if (needsBackupAndReset(messagesFile)) {
            backupAndDeleteOld(messagesFile);
        }

        this.messagesConfig = eu.okaeri.configs.ConfigManager.create(MessagesConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(), new SerdesCommons());
            it.withBindFile(messagesFile);
            it.withRemoveOrphans(true);
        });

        if (messagesFile.exists()) {
            try (InputStream in = Files.newInputStream(messagesFile.toPath())) {
                this.messagesConfig.load(in);
            } catch (IOException e) {
                if (logger != null) {
                    logger.warning("Failed to load messages.yml: " + e.getMessage());
                }
            }
        }

        if (messagesConfig.getMessagesVersion() != CURRENT_MESSAGES_VERSION) {
            messagesConfig.setMessagesVersion(CURRENT_MESSAGES_VERSION);
        }
        try (OutputStream out = Files.newOutputStream(messagesFile.toPath())) {
            this.messagesConfig.save(out);
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save messages.yml: " + e.getMessage());
            }
        }
    }

    public static boolean needsBackupAndReset(File messagesFile) {
        if (messagesFile == null || !messagesFile.exists()) {
            return false;
        }
        YamlConfiguration raw = YamlConfiguration.loadConfiguration(messagesFile);
        return raw.getInt("messages-version", -1) != CURRENT_MESSAGES_VERSION;
    }

    private void backupAndDeleteOld(File file) {
        try {
            File backupFile = new File(file.getParentFile(), file.getName() + ".bk");
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(file.toPath());
            if (logger != null) {
                logger.info("Created v1 safety backup: " + file.getName() + " -> " + backupFile.getName());
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to create backup for " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public Component parseMessage(String template) {
        if (template == null || template.isBlank()) {
            return Component.empty();
        }
        String prefix = messagesConfig != null && messagesConfig.getMessages() != null
                ? messagesConfig.getMessages().getPrefix()
                : "";
        String expanded = template.replace("{prefix}", prefix != null ? prefix : "");
        return parseComponent(expanded);
    }

    public Component parseMessage(String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            return Component.empty();
        }
        String prefix = messagesConfig != null && messagesConfig.getMessages() != null
                ? messagesConfig.getMessages().getPrefix()
                : "";
        String expanded = template.replace("{prefix}", prefix != null ? prefix : "");
        if (placeholders == null || placeholders.isEmpty()) {
            return parseComponent(expanded);
        }
        // Parse MiniMessage structure first when placeholders may contain raw user text,
        // or replace known formatting placeholders safely.
        Component result = parseComponent(expanded);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";
            Component replacement = val.contains("<") || val.contains("&") ? parseComponent(val) : Component.text(val);
            result = result.replaceText(t -> t.matchLiteral("{" + key + "}").replacement(replacement));
        }
        return result;
    }

    public static Component parseComponent(String message) {
        if (message == null || message.isBlank()) return Component.empty();

        String formatted = message;
        if (formatted.contains("&#")) {
            Matcher matcher = HEX_PATTERN.matcher(formatted);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
            }
            matcher.appendTail(sb);
            formatted = sb.toString();
        }

        if (formatted.contains("<") && formatted.contains(">")) {
            formatted = legacyToMiniMessage(formatted);
            try {
                return MINI_MESSAGE.deserialize(formatted);
            } catch (Exception ignored) {
            }
        }

        return LEGACY_SERIALIZER.deserialize(message);
    }

    public boolean sendIfNotBlank(Audience target, String rawTemplate) {
        if (target == null || rawTemplate == null || rawTemplate.isBlank()) return false;
        target.sendMessage(parseMessage(rawTemplate));
        return true;
    }

    public boolean sendIfNotBlank(Audience target, String rawTemplate, Map<String, String> placeholders) {
        if (target == null || rawTemplate == null || rawTemplate.isBlank()) return false;
        target.sendMessage(parseMessage(rawTemplate, placeholders));
        return true;
    }

    public boolean sendIfNotBlank(Audience target, String rawTemplate, Component rendered) {
        if (target == null || rawTemplate == null || rawTemplate.isBlank()) return false;
        target.sendMessage(rendered);
        return true;
    }

    private static String legacyToMiniMessage(String input) {
        Matcher matcher = LEGACY_CODE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder(input.length());
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String tag = LEGACY_TAGS.get(code);
            matcher.appendReplacement(sb, tag != null ? tag : matcher.group(0));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
