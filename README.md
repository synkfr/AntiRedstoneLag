# AntiRedstoneLag

[![Version](https://img.shields.io/badge/version-2.0.0-blue?style=flat-square)](https://github.com/synkfr/AntiRedstoneLag/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.6--1.21.11-green?style=flat-square)](https://papermc.io/)
[![License](https://img.shields.io/badge/license-MIT-orange?style=flat-square)](LICENSE)

AntiRedstoneLag stops runaway redstone clocks, 0-tick piston loops, crafter/hopper spam, and entity lag on Paper, Purpur, and Folia servers without destroying player farms.

Full documentation lives at [synkfr.github.io/AntiRedstoneLag](https://synkfr.github.io/AntiRedstoneLag/).

## What is new in v2.0.0

- **SMP-Safe Vanilla Farm Defaults**: Requires sustained multi-interval spikes (`chunk-threshold: 800`, `block-threshold: 30`), keeps `Hopper_Minecart`/`Chest_Minecart`/`Furnace_Minecart`/boats immune in ClearLagg, and ignores brief crop/sorter/piston bursts.
- **Staff Inventory GUI (`/arl gui`)**: Interactive `#1F1F1F` dashboard to cycle protection modes, toggle alerts/inspector, teleport to hotspots, manage snapshots, unfreeze/unlock regions, and trigger ClearLagg.
- **Persistent SQLite / JSON Storage**: Block owners and forensic snapshots survive chunk unloads and server restarts, with automatic migration of v1 `stats.yml`.
- **Safe v1 → v2 Upgrade**: Automatically backs up v1 files to `config.yml.bk` and `messages.yml.bk`, migrates compatible v1 settings into `config.yml`, and refreshes `messages.yml` in clean MiniMessage style.
- **Multi-Pattern Clock Fingerprinter & Specialized Detectors**: Circular pulse ring buffer catches steady and alternating clocks without penalizing storage bursts, plus piston, crafter, optional hopper, and minecart-cluster protections.
- **Visual In-World Inspector (`/arl inspect`)**: BossBar, ActionBar indicator, chunk corner pillars, and color-coded block highlight particles.
- **External Integrations**: Optional PlaceholderAPI (`%arl_*%`) expansion and asynchronous Discord Webhook alerts.

## Installation

1. Download `AntiRedstoneLag-2.0.0.jar` from [Modrinth](https://modrinth.com/plugin/antiredstonelag) or [GitHub Releases](https://github.com/synkfr/AntiRedstoneLag/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server to generate `config.yml` and `messages.yml` (or upgrade seamlessly from v1).

Requires Java 21 or newer on Paper, Purpur, or Folia (Minecraft 1.21.6+).

## Commands

| Command | Purpose | Permission |
| :--- | :--- | :--- |
| `/arl gui` | Open the interactive Staff Inventory GUI | `antiredstonelag.gui` |
| `/arl redstone <on\|off\|notify\|status>` | Switch protection on, off, or notify-only | `antiredstonelag.redstone` |
| `/arl alerts <on\|off\|status>` | Mute or enable staff lag alerts | `antiredstonelag.alerts` |
| `/arl inspect [seconds]` | Toggle BossBar, ActionBar, and particle inspector | `antiredstonelag.inspect` |
| `/arl hotspots [page\|export]` | Show or export active redstone clusters | `antiredstonelag.hotspots` |
| `/arl snapshot [list\|view\|tp\|highlight\|delete\|clear]` | Inspect, highlight, or teleport to lag sites | `antiredstonelag.snapshot` |
| `/arl clear [items\|all\|mobs\|projectiles\|xp\|vehicles\|count\|cancel\|timer\|pause\|resume]` | Clear ground items and lag entities | `antiredstonelag.clear` |
| `/arl unfreeze <all\|here>` | Unfreeze throttled clocks | `antiredstonelag.control` |
| `/arl unlock <all\|here>` | Unlock locked chunks | `antiredstonelag.control` |
| `/arl whitelist <add\|remove\|list> [world cx cz]` | Edit monitored chunk whitelist | `antiredstonelag.control` |
| `/arl stats` | Show monitored chunks, blocks, and mitigations | `antiredstonelag.stats` |
| `/arl logs [latest\|tail]` | View latest log metadata or recent log entries | `antiredstonelag.logs` |
| `/arl reload` | Reload `config.yml`, `messages.yml`, and schedulers | `antiredstonelag.reload` |
| `/arl help` | List available commands | `antiredstonelag.use` |

Players with `antiredstonelag.bypass` are exempt on blocks they placed.

## Building from source

```bash
mvn clean package
```

The shaded jar is written to `target/AntiRedstoneLag-2.0.0.jar`.

## Disclosures

- Uses bStats metrics (plugin ID `27839`), toggleable in `plugins/bStats/config.yml`.
- Checks `https://api.modrinth.com/v2/project/5UOt11Yc/version` on startup for newer release builds.
- AI coding tools were used during maintenance and development under human review.

## Links

- Documentation: [synkfr.github.io/AntiRedstoneLag](https://synkfr.github.io/AntiRedstoneLag/)
- Discord: [discord.gg/UAgsmk6KEr](https://discord.gg/UAgsmk6KEr)
- Issues: [github.com/synkfr/AntiRedstoneLag/issues](https://github.com/synkfr/AntiRedstoneLag/issues)
- License: [MIT](LICENSE)
