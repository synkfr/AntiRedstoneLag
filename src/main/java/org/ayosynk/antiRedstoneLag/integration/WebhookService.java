package org.ayosynk.antiRedstoneLag.integration;

import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Asynchronous Discord webhook dispatcher for critical redstone and TPS lag events.
 */
public class WebhookService {
    private final ConfigManager configManager;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public WebhookService(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void sendAlert(String eventType, String title, String description) {
        PluginConfig.DiscordWebhookConfig cfg = configManager.getPluginConfig().getIntegrations().getDiscord();
        if (!cfg.isEnabled()) return;
        String url = cfg.getWebhookUrl().trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) return;
        if (cfg.getEvents() == null || !cfg.getEvents().contains(eventType)) return;

        long now = System.currentTimeMillis();
        long cooldownMs = cfg.getCooldownSeconds() * 1000L;
        Long lastSent = cooldowns.get(eventType);
        if (lastSent != null && (now - lastSent) < cooldownMs) {
            return;
        }
        cooldowns.put(eventType, now);

        int colorDecimal = switch (eventType) {
            case "LOCKDOWN", "TPS_LAG", "REMOVAL" -> 0xE20000;
            case "SNAPSHOT" -> 0xE2B700;
            default -> 0x00E262;
        };

        String payload = String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d,\"timestamp\":\"%s\"}]}",
                escapeJson(title),
                escapeJson(description),
                colorDecimal,
                Instant.now().toString()
        );

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "AntiRedstoneLag/2.0.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        if (configManager.isDebugMode() && logger != null) {
                            logger.warning("Discord webhook failed: " + ex.getMessage());
                        }
                        return null;
                    });
        } catch (Exception e) {
            if (configManager.isDebugMode() && logger != null) {
                logger.warning("Invalid Discord webhook configuration: " + e.getMessage());
            }
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
