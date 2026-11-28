# Commands and permissions

Every command starts with `/arl` or `/antiredstonelag`.

## Staff GUI and protection

| Command | Behavior | Permission |
| :--- | :--- | :--- |
| `/arl gui` | Open the interactive Staff Inventory GUI (`#1F1F1F` panels) | `antiredstonelag.gui` |
| `/arl redstone on` | Enable protection using your configured `removal-action` (switches `NOTIFY` back to `FREEZE`) | `antiredstonelag.redstone` |
| `/arl redstone off` | Disable redstone throttling and clear active freezes and chunk locks | `antiredstonelag.redstone` |
| `/arl redstone notify` | Keep protection in notification-only mode so farms run untouched | `antiredstonelag.redstone` |
| `/arl redstone status` | Print current protection state, action, and alert setting | `antiredstonelag.redstone` |
| `/arl alerts on` | Enable admin redstone and lag notifications | `antiredstonelag.alerts` |
| `/arl alerts off` | Mute admin notifications without changing redstone rules | `antiredstonelag.alerts` |
| `/arl alerts status` | Show current alert, TPS lag, and lag-only flags | `antiredstonelag.alerts` |
| `/arl unfreeze <all\|here>` | Manually unfreeze all clocks or clocks in your current chunk | `antiredstonelag.control` |
| `/arl unlock <all\|here>` | Manually unlock all locked chunks or your current chunk | `antiredstonelag.control` |
| `/arl whitelist <add\|remove\|list> [world cx cz]` | Add, remove, or list monitored whitelist chunks | `antiredstonelag.control` |

## Diagnostics and maintenance

| Command | Behavior | Permission |
| :--- | :--- | :--- |
| `/arl inspect [seconds]` | Toggle the live BossBar, ActionBar, chunk borders, and block highlight particles | `antiredstonelag.inspect` |
| `/arl hotspots [page\|export]` | Cluster adjacent active chunks or export them to `plugins/AntiRedstoneLag/logs/` | `antiredstonelag.hotspots` |
| `/arl snapshot [list\|view\|tp\|highlight\|delete\|clear]` | Inspect, highlight, delete, or teleport to persisted lag snapshots | `antiredstonelag.snapshot` |
| `/arl clear [type\|count\|cancel\|timer\|pause\|resume]` | Clear `items`, `all`, `mobs`, `projectiles`, `xp`, or `vehicles`, or control the timer | `antiredstonelag.clear` |
| `/arl stats` | Print monitored chunks, blocks, freezes, lockdowns, mitigations, and TPS percentage | `antiredstonelag.stats` |
| `/arl logs [latest\|tail [lines]]` | Show log status, latest log file metadata, or recent log lines | `antiredstonelag.logs` |
| `/arl reload` | Reload `config.yml`, `messages.yml`, ClearLagg rules, and scheduler intervals | `antiredstonelag.reload` |
| `/arl help` | Print command usage | `antiredstonelag.use` |

## Additional permissions

| Permission | Default | Effect |
| :--- | :--- | :--- |
| `antiredstonelag.bypass` | `false` | Exempts redstone placed by the player |
| `antiredstonelag.admin` | `op` | Receives Modrinth update notices on join |
| `antiredstonelag.*` | `op` | Grants all plugin permissions |
