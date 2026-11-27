package org.ayosynk.antiRedstoneLag.manager;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EntityFilterRule {

    private enum TargetGroup {
        NONE,
        ANY,
        ITEM,
        PROJECTILE,
        MONSTER,
        ANIMAL,
        VEHICLE,
        BOAT,
        CHEST_BOAT,
        MOB,
        SPECIFIC
    }

    private static final Map<String, String> LEGACY_TYPE_ALIASES = Map.of(
            "MINECART_CHEST", "CHEST_MINECART",
            "MINECART_HOPPER", "HOPPER_MINECART",
            "MINECART_TNT", "TNT_MINECART",
            "MINECART_FURNACE", "FURNACE_MINECART",
            "MINECART_MOB_SPAWNER", "SPAWNER_MINECART",
            "MINECART_COMMAND", "COMMAND_BLOCK_MINECART",
            "THROWN_EXP_BOTTLE", "EXPERIENCE_BOTTLE",
            "PRIMED_TNT", "TNT"
    );

    private final String originalRule;
    private final TargetGroup targetGroup;
    private final EntityType specificType;
    private final List<Condition> conditions = new ArrayList<>();

    @FunctionalInterface
    private interface Condition {
        boolean test(Entity entity);
    }

    public static EntityFilterRule parse(String ruleString) {
        if (ruleString == null || ruleString.trim().isEmpty()) return null;
        return new EntityFilterRule(ruleString.trim());
    }

    private EntityFilterRule(String ruleString) {
        this.originalRule = ruleString;
        String[] tokens = ruleString.split("\\s+");
        String targetToken = tokens[0].toUpperCase(Locale.ROOT);
        targetToken = LEGACY_TYPE_ALIASES.getOrDefault(targetToken, targetToken);

        if (targetToken.equals("*") || targetToken.equals("ALL")) {
            this.targetGroup = TargetGroup.ANY;
            this.specificType = null;
        } else if (targetToken.equals("ITEM") || targetToken.equals("ITEMS")) {
            this.targetGroup = TargetGroup.ITEM;
            this.specificType = null;
        } else if (targetToken.equals("PROJECTILE") || targetToken.equals("PROJECTILES")) {
            this.targetGroup = TargetGroup.PROJECTILE;
            this.specificType = null;
        } else if (targetToken.equals("MONSTER") || targetToken.equals("MONSTERS")) {
            this.targetGroup = TargetGroup.MONSTER;
            this.specificType = null;
        } else if (targetToken.equals("ANIMAL") || targetToken.equals("ANIMALS")) {
            this.targetGroup = TargetGroup.ANIMAL;
            this.specificType = null;
        } else if (targetToken.equals("VEHICLE") || targetToken.equals("VEHICLES")) {
            this.targetGroup = TargetGroup.VEHICLE;
            this.specificType = null;
        } else if (targetToken.equals("BOAT") || targetToken.equals("BOATS")) {
            this.targetGroup = TargetGroup.BOAT;
            this.specificType = null;
        } else if (targetToken.equals("CHEST_BOAT") || targetToken.equals("CHEST_BOATS")) {
            this.targetGroup = TargetGroup.CHEST_BOAT;
            this.specificType = null;
        } else if (targetToken.equals("MOB") || targetToken.equals("MOBS")) {
            this.targetGroup = TargetGroup.MOB;
            this.specificType = null;
        } else {
            EntityType resolved = null;
            try {
                resolved = EntityType.valueOf(targetToken);
            } catch (IllegalArgumentException ignored) {
                for (EntityType et : EntityType.values()) {
                    if (et.name().equalsIgnoreCase(targetToken)) {
                        resolved = et;
                        break;
                    }
                }
            }
            if (resolved != null) {
                this.targetGroup = TargetGroup.SPECIFIC;
                this.specificType = resolved;
            } else {
                // Unknown entity names must never degrade into wildcard matching.
                this.targetGroup = TargetGroup.NONE;
                this.specificType = null;
            }
        }

        for (int i = 1; i < tokens.length; i++) {
            String mod = tokens[i];
            String lower = mod.toLowerCase(Locale.ROOT);

            if (lower.equals("onground")) {
                conditions.add(e -> {
                    if (e instanceof AbstractArrow arrow) return arrow.isInBlock();
                    return e.isOnGround();
                });
            } else if (lower.equals("!onground")) {
                conditions.add(e -> {
                    if (e instanceof AbstractArrow arrow) return !arrow.isInBlock();
                    return !e.isOnGround();
                });
            } else if (lower.equals("ismounted")) {
                conditions.add(e -> !e.getPassengers().isEmpty() || e.getVehicle() != null);
            } else if (lower.equals("!ismounted")) {
                conditions.add(e -> e.getPassengers().isEmpty() && e.getVehicle() == null);
            } else if (lower.equals("hasname")) {
                conditions.add(e -> e.customName() != null);
            } else if (lower.equals("!hasname")) {
                conditions.add(e -> e.customName() == null);
            } else if (lower.equals("istamed")) {
                conditions.add(e -> e instanceof Tameable t && t.isTamed());
            } else if (lower.equals("!istamed")) {
                conditions.add(e -> !(e instanceof Tameable t) || !t.isTamed());
            } else if (lower.equals("isleashed")) {
                conditions.add(e -> e instanceof LivingEntity le && le.isLeashed());
            } else if (lower.equals("!isleashed")) {
                conditions.add(e -> !(e instanceof LivingEntity le) || !le.isLeashed());
            } else if (lower.equals("isbaby")) {
                conditions.add(e -> e instanceof Ageable a && !a.isAdult());
            } else if (lower.equals("!isbaby")) {
                conditions.add(e -> !(e instanceof Ageable a) || a.isAdult());
            } else if (lower.equals("inwater")) {
                conditions.add(Entity::isInWater);
            } else if (lower.equals("!inwater")) {
                conditions.add(e -> !e.isInWater());
            } else if (lower.startsWith("livetime>") || lower.startsWith("livetime=")) {
                try {
                    int ticks = Integer.parseInt(mod.replaceAll("[^0-9]", ""));
                    conditions.add(e -> e.getTicksLived() >= ticks);
                } catch (NumberFormatException ignored) {
                }
            } else if (lower.startsWith("livetime<")) {
                try {
                    int ticks = Integer.parseInt(mod.replaceAll("[^0-9]", ""));
                    conditions.add(e -> e.getTicksLived() < ticks);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public boolean isValid() {
        return targetGroup != TargetGroup.NONE;
    }

    public boolean matches(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Player) return false;

        switch (targetGroup) {
            case NONE:
                return false;
            case ANY:
                break;
            case ITEM:
                if (!(entity instanceof Item)) return false;
                break;
            case PROJECTILE:
                if (!(entity instanceof Projectile)) return false;
                break;
            case MONSTER:
                if (!(entity instanceof Monster)) return false;
                break;
            case ANIMAL:
                if (!(entity instanceof Animals)) return false;
                break;
            case VEHICLE:
                if (!(entity instanceof Vehicle)) return false;
                break;
            case BOAT:
                if (!(entity instanceof Boat)) return false;
                break;
            case CHEST_BOAT:
                if (!(entity instanceof ChestBoat)) return false;
                break;
            case MOB:
                if (!(entity instanceof Mob)) return false;
                break;
            case SPECIFIC:
                if (entity.getType() != specificType) return false;
                break;
        }

        for (Condition cond : conditions) {
            if (!cond.test(entity)) {
                return false;
            }
        }

        return true;
    }

    public String getOriginalRule() {
        return originalRule;
    }
}
