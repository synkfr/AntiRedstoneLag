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
  min-item-age-seconds: 30
  merge-items: false
  chunk-entity-caps:
    enabled: false
    max-mobs-per-chunk: 40
    max-items-per-chunk: 100
  tps-emergency-threshold: 0.0
```

`broadcast-mode` supports `CHAT`, `ACTION_BAR`, `TITLE`, `SUBTITLE`, `ALL`, and `NONE`.

## v2 Improvements

- **Item Age Protection (`min-item-age-seconds`)**: Ground items dropped more recently than this age (default 30s) are protected during scheduled auto-clears.
- **SMP Vehicle Immunity**: `Hopper_Minecart`, `Chest_Minecart`, `Furnace_Minecart`, and boats are excluded from default removal and whitelisted so farm collectors and docks remain untouched.
- **Chunk Entity Density Caps (`chunk-entity-caps`)**: Optional per-chunk limits for unwhitelisted mobs and dropped items.
- **Per-World Toggle**: Set `worlds.<worldName>.clearlagg-enabled: false` to exclude specific worlds from automatic or manual ClearLagg sweeps.
- **Timer Controls**: Use `/arl clear pause`, `/arl clear resume`, `/arl clear cancel`, or the Staff GUI ClearLagg panel (`/arl gui`).

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
