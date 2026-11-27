# ClearLagg rules

ClearLagg runs on a fixed timer (`interval-seconds`) and optionally on emergency TPS drops (`tps-emergency-threshold`) on Paper. On Folia, each entity is checked and removed on its owning region thread.

## Default configuration

```yaml
clearlagg:
  enabled: true
  interval-seconds: 300
  countdown-seconds: [60, 30, 10, 5, 4, 3, 2, 1]
  broadcast-mode: CHAT
  clear-ground-items: true
  tps-emergency-threshold: 0.0
```

`broadcast-mode` supports `CHAT`, `ACTION_BAR`, `TITLE`, `SUBTITLE`, `ALL`, and `NONE`.

## Rule syntax

Each line in `remove-entities` or `entity-whitelist` starts with a target followed by optional conditions separated by spaces.

Targets:
- `*` or `ALL`
- Group keywords: `ITEM`, `PROJECTILE`, `MONSTER`, `ANIMAL`, `VEHICLE`, `BOAT`, `CHEST_BOAT`, `MOB`
- Any Bukkit `EntityType` name (for example `Arrow`, `Tnt_Minecart`, `Chest_Minecart`, `Hopper_Minecart`, `Furnace_Minecart`)

Conditions:
- `onGround` and `!onGround` (checks `isInBlock()` for arrows)
- `isMounted` and `!isMounted`
- `hasName` and `!hasName`
- `isTamed` and `!isTamed`
- `isLeashed` and `!isLeashed`
- `isBaby` and `!isBaby`
- `inWater` and `!inWater`
- `liveTime>N`, `liveTime=N`, and `liveTime<N` (in ticks)

Unrecognized target names are ignored and logged at startup instead of matching entities. Players are never cleared by any rule.

## Default protections

The default `entity-whitelist` protects:
- Players, Villagers, Iron Golems, Allays, Armor Stands, Item Frames, Glow Item Frames, Paintings, Leash Knots, Interactions, and Display entities
- Any entity matching `hasName`, `isTamed`, `isLeashed`, or `isMounted`

`item-blacklist` protects dropped Nether Stars, Beacons, Totems of Undying, Elytras, Diamonds, Diamond Blocks, Netherite gear, and Netherite blocks/ingots.
