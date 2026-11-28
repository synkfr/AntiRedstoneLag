# Configuration

Configuration lives in `plugins/AntiRedstoneLag/config.yml` and can be reloaded in game with `/arl reload`.

## Core thresholds and actions

```yaml
config-version: 2
chunk-threshold: 800
block-threshold: 30
reset-interval-ticks: 20
redstone-enabled: true
removal-action: FREEZE
```

By default, thresholds require activity across two consecutive intervals (unless an extreme 2x chunk or 3x block burst occurs) so brief vanilla crop, melon, sugarcane, and sorter waves never false-flag.

`removal-action` accepts five modes:

| Mode | Effect when threshold trips |
| :--- | :--- |
| `FREEZE` | Cancels the signal for `freeze.duration-seconds`, notifies on resume (`freeze.notify-on-resume`), and escalates to lockdown after `max-freeze-attempts` |
| `NOTIFY` | Never cancels, freezes, locks down, or breaks redstone; sends staff coordinates, counts, and culprit name |
| `DISABLE` | Cancels the block's redstone current until the next `reset-interval-ticks` cycle |
| `DROP` | Breaks the offending block naturally and drops it as an item |
| `REMOVE` | Replaces the offending block with air |

## Persistent storage (`storage`)

```yaml
storage:
  backend: AUTO
  sqlite-file: "data/antiredstonelag.db"
  persist-owners: true
  persist-snapshots: true
  owner-retention-days: 30
```

- `AUTO` uses SQLite WAL mode when `sqlite-jdbc` is available and falls back cleanly to atomic JSON files in `plugins/AntiRedstoneLag/data/`.
- Block ownership survives chunk unloads and server restarts, and v1 `stats.yml` is migrated automatically.

## Specialized detectors & multi-pattern fingerprinting

```yaml
fingerprint:
  enabled: true
  clock-strictness: 0.85
  history-size: 8
  max-interval-ms: 500
  variance-tolerance-ms: 45

detectors:
  piston-loop-threshold: 16
  hopper-limiter:
    enabled: false
    max-transfers-per-interval: 64
  crafter-limiter:
    enabled: true
    max-crafts-per-interval: 40
  minecart-cluster:
    enabled: true
    max-minecarts-per-block: 16
    action: REMOVE_EXCESS
```

## Per-world overrides (`worlds`)

Override `chunk-threshold`, `block-threshold`, `removal-action`, or `clearlagg-enabled` for specific worlds:

```yaml
worlds:
  world_nether:
    chunk-threshold: 350
    block-threshold: 12
    removal-action: FREEZE
    clearlagg-enabled: true
```

## Integrations (`integrations`)

- **PlaceholderAPI**: Registers `%arl_status%`, `%arl_mode%`, `%arl_alerts%`, `%arl_tps%`, `%arl_mspt%`, `%arl_chunks_monitored%`, `%arl_blocks_monitored%`, `%arl_frozen_count%`, `%arl_locked_count%`, `%arl_removals_total%`, `%arl_removals_today%`, `%arl_freezes_total%`, `%arl_lockdowns_total%`, `%arl_entities_cleared%`, and `%arl_clear_countdown%`.
- **Discord Webhooks**: Sends async embeds for `LOCKDOWN`, `TPS_LAG`, `REMOVAL`, and `SNAPSHOT` events with configurable cooldowns.
