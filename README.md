# AntiRedstoneLag

[![Version](https://img.shields.io/badge/version-26.5-blue?style=flat-square)](https://github.com/synkfr/AntiRedstoneLag/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4--26.3-green?style=flat-square)](https://papermc.io/)
[![License](https://img.shields.io/badge/license-MIT-orange?style=flat-square)](LICENSE)

AntiRedstoneLag stops runaway redstone clocks and 0-tick piston loops on Paper, Purpur, and Folia servers without destroying player farms.

Full documentation lives at [synkfr.github.io/AntiRedstoneLag](https://synkfr.github.io/AntiRedstoneLag/).

## What it does

When redstone updates in a chunk pass the configured limits, the plugin can freeze the clock temporarily, send alerts without blocking anything, cancel the signal, or remove the block.

- **Non-destructive by default (`FREEZE`)**: Pauses fast clocks for 15 seconds and cancels runaway signals without breaking redstone dust or pistons.
- **Notification-only mode (`/arl redstone notify`)**: Lets every farm run untouched and sends staff the coordinates, update counts, and player name when a circuit spikes.
- **Instant toggle (`/arl redstone off` and `/arl redstone on`)**: Turns redstone limiting off or back on from in-game or console and clears any active freezes or chunk locks right away.
- **TPS lag culprit lookup**: When TPS drops below `adaptive.min-tps` or MSPT passes `adaptive.target-mspt` on Paper, the plugin finds the busiest redstone chunk and notifies staff who is running it.
- **Live inspector and snapshots**: Check chunk update rates live with `/arl inspect`, inspect recent trips with `/arl snapshot`, or group busy areas with `/arl hotspots`.
- **ClearLagg entity cleaner**: Removes dropped items and configured entities on a timer while keeping named, tamed, leashed, mounted, and SMP mobs safe.

## Installation

1. Download the jar from [Modrinth](https://modrinth.com/plugin/antiredstonelag) or [GitHub Releases](https://github.com/synkfr/AntiRedstoneLag/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server to generate `config.yml` and `messages.yml`.

Requires Java 21 or newer on Paper, Purpur, or Folia (Minecraft 1.21.4 through 26.3).

## Commands

| Command | Purpose | Permission |
| :--- | :--- | :--- |
| `/arl redstone <on\|off\|notify\|status>` | Switch protection on, off, or notify-only | `antiredstonelag.redstone` |
| `/arl alerts <on\|off\|status>` | Mute or enable staff lag alerts | `antiredstonelag.alerts` |
| `/arl inspect [seconds]` | Toggle the live BossBar chunk meter | `antiredstonelag.inspect` |
| `/arl hotspots [page\|export]` | Show or export active redstone clusters | `antiredstonelag.hotspots` |
| `/arl snapshot [list\|view\|tp\|clear]` | View or teleport to captured lag sites | `antiredstonelag.snapshot` |
| `/arl clear [items\|all\|mobs\|projectiles\|xp\|vehicles\|count\|cancel\|timer]` | Clear ground items and lag entities | `antiredstonelag.clear` |
| `/arl stats` | Show monitored chunks, blocks, and removals | `antiredstonelag.stats` |
| `/arl logs [download]` | Show log status and latest log file details | `antiredstonelag.logs` |
| `/arl reload` | Reload `config.yml` and `messages.yml` | `antiredstonelag.reload` |
| `/arl help` | List available commands | `antiredstonelag.use` |

Players with `antiredstonelag.bypass` are exempt on blocks they placed.

## Building from source

```bash
mvn clean package
```

The shaded jar is written to `target/AntiRedstoneLag-26.5.jar`.

## Disclosures

- Uses bStats metrics (plugin ID `27839`), toggleable in `plugins/bStats/config.yml`.
- Checks `https://api.modrinth.com/v2/project/5UOt11Yc/version` on startup for newer release builds.
- AI coding tools were used during maintenance and bug fixes under human review.

## Links

- Documentation: [synkfr.github.io/AntiRedstoneLag](https://synkfr.github.io/AntiRedstoneLag/)
- Discord: [discord.gg/UAgsmk6KEr](https://discord.gg/UAgsmk6KEr)
- Issues: [github.com/synkfr/AntiRedstoneLag/issues](https://github.com/synkfr/AntiRedstoneLag/issues)
- License: [MIT](LICENSE)
