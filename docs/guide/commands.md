# Commands and permissions

Every command starts with `/arl` or `/antiredstonelag`.

## Redstone and alerts

| Command | Behavior | Permission |
| :--- | :--- | :--- |
| `/arl redstone on` | Enable protection using your configured `removal-action` (switches `NOTIFY` back to `FREEZE`) | `antiredstonelag.redstone` |
| `/arl redstone off` | Disable redstone throttling and clear active freezes and chunk locks | `antiredstonelag.redstone` |
| `/arl redstone notify` | Keep protection in notification-only mode so farms run untouched | `antiredstonelag.redstone` |
| `/arl redstone status` | Print current protection state, action, and alert setting | `antiredstonelag.redstone` |
| `/arl alerts on` | Enable admin redstone and lag notifications | `antiredstonelag.alerts` |
| `/arl alerts off` | Mute admin notifications without changing redstone rules | `antiredstonelag.alerts` |
| `/arl alerts status` | Show current alert, TPS lag, and lag-only flags | `antiredstonelag.alerts` |

## Diagnostics and maintenance

| Command | Behavior | Permission |
| :--- | :--- | :--- |
| `/arl inspect [seconds]` | Toggle the live BossBar showing chunk UPS, MSPT, and TPS (default `30`, clamped `5` to `300`) | `antiredstonelag.inspect` |
| `/arl hotspots [page\|export]` | Cluster adjacent active chunks or export them to `plugins/AntiRedstoneLag/logs/` | `antiredstonelag.hotspots` |
| `/arl snapshot [list\|view\|tp\|clear]` | Inspect or teleport to captured 7x7x7 lag snapshots | `antiredstonelag.snapshot` |
| `/arl clear [type]` | Clear `items`, `all`, `mobs`, `projectiles`, `xp`, or `vehicles`, or run `count`, `cancel`, `timer` | `antiredstonelag.clear` |
| `/arl stats` | Print monitored chunks, blocks, removal counts, and TPS percentage | `antiredstonelag.stats` |
| `/arl logs [download]` | Show log status or print metadata for the newest log file | `antiredstonelag.logs` |
| `/arl reload` | Reload `config.yml`, `messages.yml`, ClearLagg rules, and listener caches | `antiredstonelag.reload` |
| `/arl help` | Print command usage | `antiredstonelag.use` |

## Additional permissions

| Permission | Default | Effect |
| :--- | :--- | :--- |
| `antiredstonelag.bypass` | `false` | Exempts redstone placed by the player while they are online |
| `antiredstonelag.admin` | `op` | Receives Modrinth update notices on join |
| `antiredstonelag.*` | `op` | Grants all plugin permissions |
