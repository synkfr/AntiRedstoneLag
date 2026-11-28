package org.ayosynk.antiRedstoneLag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.MessagesConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageStyleTests {

    @Test
    void defaultMessagesFollowDesignMdPaletteAndFormatting(@TempDir Path tempDir) {
        MessageManager mm = new MessageManager(tempDir.toFile(), Logger.getLogger("MessageStyleTests"));
        MessagesConfig cfg = mm.getMessagesConfig();

        assertEquals(2, cfg.getMessagesVersion());
        String prefix = cfg.getMessages().getPrefix();
        assertTrue(prefix.contains("#A0A0A0"));
        assertTrue(prefix.contains("#00E262"));
        assertTrue(prefix.contains("AntiRedstoneLag"));

        // Inventory GUI titles must use #1F1F1F Dark gray per design.md
        assertTrue(cfg.getGui().getTitles().getMain().startsWith("<color:#1F1F1F>"));
        assertTrue(cfg.getGui().getTitles().getHotspots().startsWith("<color:#1F1F1F>"));
        assertTrue(cfg.getGui().getTitles().getSnapshots().startsWith("<color:#1F1F1F>"));
        assertTrue(cfg.getGui().getTitles().getLocked().startsWith("<color:#1F1F1F>"));
        assertTrue(cfg.getGui().getTitles().getClearlagg().startsWith("<color:#1F1F1F>"));

        // Verify placeholders render cleanly and without legacy box characters.
        Component enabled = mm.parseMessage(cfg.getMessages().getEnabled(), Map.of("version", "2.0.0"));
        String plain = PlainTextComponentSerializer.plainText().serialize(enabled);
        assertEquals("[AntiRedstoneLag] Enabled v2.0.0.", plain);
        assertFalse(plain.contains("✓"));
        assertFalse(plain.contains("┌"));

        Component stats = mm.parseMessage(cfg.getCommands().getStats(), Map.of(
                "chunks", "3",
                "blocks", "12",
                "freezes", "1",
                "lockdowns", "0",
                "total_removals", "5",
                "today_removals", "2",
                "entities_cleared", "40",
                "performance", "100"
        ));
        String statsPlain = PlainTextComponentSerializer.plainText().serialize(stats);
        assertTrue(statsPlain.contains("Chunks monitored: 3"));
        assertFalse(statsPlain.contains("│"));
    }
}
