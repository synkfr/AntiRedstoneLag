package org.ayosynk.antiRedstoneLag;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.listener.UpdateChecker;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;
import org.ayosynk.antiRedstoneLag.manager.EntityFilterRule;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCoreTests {

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T mockEntity(Class<T> iface, EntityType type, boolean mounted, Component customName) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "getPassengers" -> mounted ? List.of(proxy) : Collections.emptyList();
                    case "getVehicle" -> null;
                    case "customName" -> customName;
                    case "isOnGround" -> true;
                    case "getTicksLived" -> 200;
                    case "isInWater" -> false;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return 0;
    }

    @Test
    void unknownEntityRuleMatchesNothing() {
        EntityFilterRule invalid = EntityFilterRule.parse("NotARealEntity");
        assertNotNull(invalid);
        assertFalse(invalid.isValid());

        Cow cow = mockEntity(Cow.class, EntityType.COW, false, null);
        assertFalse(invalid.matches(cow));
    }

    @Test
    void legacyAndModernMinecartAndBoatRulesMatchAccurately() {
        EntityFilterRule legacyTnt = EntityFilterRule.parse("Minecart_Tnt");
        assertNotNull(legacyTnt);
        assertTrue(legacyTnt.isValid());

        Minecart tntCart = mockEntity(Minecart.class, EntityType.TNT_MINECART, false, null);
        Cow cow = mockEntity(Cow.class, EntityType.COW, false, null);
        assertTrue(legacyTnt.matches(tntCart));
        assertFalse(legacyTnt.matches(cow));

        EntityFilterRule boatRule = EntityFilterRule.parse("Boat !isMounted");
        assertNotNull(boatRule);
        assertTrue(boatRule.isValid());

        Boat unmountedBoat = mockEntity(Boat.class, EntityType.OAK_BOAT, false, null);
        Boat mountedBoat = mockEntity(Boat.class, EntityType.OAK_BOAT, true, null);
        ChestBoat unmountedChestBoat = mockEntity(ChestBoat.class, EntityType.OAK_CHEST_BOAT, false, null);
        assertTrue(boatRule.matches(unmountedBoat));
        assertFalse(boatRule.matches(mountedBoat));
        assertTrue(boatRule.matches(unmountedChestBoat));

        EntityFilterRule chestBoatRule = EntityFilterRule.parse("Chest_Boat !isMounted");
        assertNotNull(chestBoatRule);
        assertFalse(chestBoatRule.matches(unmountedBoat));
        assertTrue(chestBoatRule.matches(unmountedChestBoat));
    }

    @Test
    void wildcardNeverMatchesPlayers() {
        EntityFilterRule allRule = EntityFilterRule.parse("*");
        assertNotNull(allRule);
        Player player = mockEntity(Player.class, EntityType.PLAYER, false, null);
        assertFalse(allRule.matches(player));
    }

    @Test
    void blockAndChunkPackingRoundTripsAllSignedCoordinates() {
        int[][] coords = {
                {0, 64, 0},
                {-1, -64, -1},
                {29_999_999, 319, -29_999_999},
                {-29_999_999, -1, 12345}
        };
        for (int[] c : coords) {
            long packed = CounterManager.packBlock(c[0], c[1], c[2]);
            assertEquals(c[0], CounterManager.unpackBlockX(packed));
            assertEquals(c[1], CounterManager.unpackBlockY(packed));
            assertEquals(c[2], CounterManager.unpackBlockZ(packed));
        }

        long chunkPacked = CounterManager.packChunk(-123456, 654321);
        assertEquals(-123456, (int) (chunkPacked >> 32));
        assertEquals(654321, (int) chunkPacked);
    }

    @Test
    void versionComparisonHandlesUpgradesAndPreReleases() {
        assertTrue(UpdateChecker.isRemoteNewer("26.4", "26.3"));
        assertTrue(UpdateChecker.isRemoteNewer("26.3.1", "26.3"));
        assertTrue(UpdateChecker.isRemoteNewer("26.4", "26.4-SNAPSHOT"));
        assertFalse(UpdateChecker.isRemoteNewer("26.3", "26.4"));
        assertFalse(UpdateChecker.isRemoteNewer("v26.4", "26.4"));
        assertFalse(UpdateChecker.isRemoteNewer("26.4-SNAPSHOT", "26.4"));
    }

    @Test
    void messageParserHandlesHexUppercaseLegacyAndBlank() {
        assertEquals(Component.empty(), MessageManager.parseComponent(""));
        assertEquals(Component.empty(), MessageManager.parseComponent("   "));

        Component comp = MessageManager.parseComponent("&#FF6B6B[ARL] &AEnabled <bold>Now</bold>");
        String plain = PlainTextComponentSerializer.plainText().serialize(comp);
        assertEquals("[ARL] Enabled Now", plain);

        String statusMsg = "&#4ECDC4[ARL] &7Redstone protection: {status} &8| &7Alerts: {alerts}"
                .replace("{status}", "&eNOTIFY ONLY")
                .replace("{alerts}", "&aON");
        String statusPlain = PlainTextComponentSerializer.plainText().serialize(MessageManager.parseComponent(statusMsg));
        assertEquals("[ARL] Redstone protection: NOTIFY ONLY | Alerts: ON", statusPlain);
    }

    @Test
    void whitelistParserSkipsInvalidAndIndexesValidChunks() {
        Map<String, LongOpenHashSet> parsed = ConfigManager.parseWhitelistedChunks(
                List.of("world:10:-5", "bad_entry", "world:x:2"), null);
        assertTrue(parsed.containsKey("world"));
        assertTrue(parsed.get("world").contains(CounterManager.packChunk(10, -5)));
        assertEquals(1, parsed.get("world").size());
    }
}
