# Configuration

Configuration lives in `plugins/AntiRedstoneLag/config.yml` and can be reloaded in game with `/arl reload`.

## Core thresholds and actions

```yaml
chunk-threshold: 500
block-threshold: 15
reset-interval-ticks: 20
redstone-enabled: true
removal-action: FREEZE
```

`removal-action` accepts five modes:

| Mode | Effect when threshold trips |
| :--- | :--- |
| `FREEZE` | Cancels the signal for `freeze.duration-seconds`, then falls back to signal throttling or chunk lockdown without breaking blocks |
| `NOTIFY` | Never cancels, freezes, locks down, or breaks redstone; sends staff coordinates, counts, and culprit name |
| `DISABLE` | Cancels the block's redstone current until the next `reset-interval-ticks` cycle |
| `DROP` | Breaks the offending block naturally and drops it as an item |
| `REMOVE` | Replaces the offending block with air |

## TPS lag alerts and blank messages

```yaml
alerts:
  enabled: true
  log-to-console: true
  notify-on-tps-lag: true
  only-when-lagging: false
```

- `notify-on-tps-lag`: On Paper and Purpur, when TPS drops below `adaptive.min-tps` or MSPT rises above `adaptive.target-mspt`, AntiRedstoneLag checks the busiest redstone chunk from the last interval and alerts staff with the culprit's name if redstone activity is high.
- `only-when-lagging`: Suppresses threshold, freeze, and lockdown notifications whenever server TPS and MSPT are healthy.
- Setting any string in `messages.yml` to `""` suppresses that line completely without sending an empty chat message.

Note: Folia regions tick independently and do not expose global server TPS or MSPT, so `notify-on-tps-lag` and `only-when-lagging` apply on Paper and Purpur while threshold `NOTIFY` alerts work on both Paper and Folia.

## Adaptive scaling, fingerprinting, and proximity

```yaml
freeze:
  enabled: true
  duration-seconds: 15
  max-freeze-attempts: 3

adaptive:
  enabled: true
  target-mspt: 40.0
  min-tps: 18.0
  healthy-headroom-multiplier: 1.5

fingerprint:
  enabled: true
  clock-strictness: 0.7

proximity:
  enabled: true
  player-radius: 96
  unattended-strict-multiplier: 0.6
```

- When `mspt <= 35.0` and `tps >= 19.5`, thresholds multiply by `healthy-headroom-multiplier`. When `tps < min-tps` or `mspt >= 50.0`, thresholds halve (`0.5x`).
- When no player is within `player-radius` blocks, thresholds scale by `unattended-strict-multiplier`.
- Whitelist mode (`whitelist.enabled: true`) restricts monitoring exclusively to listed `world:chunkX:chunkZ` entries.
