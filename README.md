<div align="center">

[![Version](https://img.shields.io/badge/version-26.2-blue?style=for-the-badge&logo=github)](https://github.com/synkfr/AntiRedstoneLag/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.6--26.2-green?style=for-the-badge&logo=minecraft)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-MIT-orange?style=for-the-badge)](LICENSE)
[![Discord](https://img.shields.io/discord/1378591879393710110?style=for-the-badge&logo=discord&label=Discord)](https://discord.gg/fGyDyp3Ak4)

# AntiRedstoneLag

**Advanced Redstone Lag Prevention, Forensic Diagnostics, and ClearLagg Engine for Modern Minecraft Servers (Paper & Folia 1.21.6 - 26.2)**

[Features](#features) | [Performance](#performance) | [Installation](#installation) | [Commands and Permissions](#commands-and-permissions) | [ClearLagg Engine](#clearlagg-engine) | [Disclosures](#project-disclosures-and-ai-policy)

</div>

---

## About AntiRedstoneLag

AntiRedstoneLag is a lightweight, high-performance server protection plugin designed to eliminate redstone lag machines, 0-tick piston loops, and runaway clocks without breaking legitimate vanilla farms. 

Built natively for Paper and Folia regional threading, it combines periodicity clock fingerprinting, adaptive MSPT/TPS sensitivity scaling, non-destructive freezing, 3D forensic snapshots, live BossBar HUD diagnostics, and an integrated ClearLagg engine.

---

## Features

### Modern 1.21+ Component Support
- Full coverage of modern Minecraft components: Crafters, Copper Bulbs (all oxidation and waxed states), Calibrated Sculk Sensors, and Chiseled Bookshelves.
- Real-time piston velocity tracking to intercept 0-tick push/pull crash engines.

### Periodicity Clock Fingerprinting (Farm Immunity)
- Analyzes the interval delta between consecutive redstone pulses.
- Distinguishes strictly periodic loops (lag machines with near-zero interval variance) from bursty sorting systems, sugarcane farms, iron farms, and melon harvesters.
- Legitimate player farms remain 100% functional without false flags.

### Dynamic Adaptive Sensitivity (MSPT & TPS Scaling)
- Automatically grants higher threshold headroom when server MSPT is healthy (<= 35ms / 20.0 TPS).
- Dynamically tightens limits when the server is under heavy load (MSPT >= 50ms or TPS < 18.0) to protect the tick rate.

### Non-Destructive Tiered Freezing
- Temporarily pause-throttles runaway clocks (default 15 seconds) and notifies the owner before escalating.
- Only escalates to item dropping or removal if a machine continuously violates limits after unfreezing.

### Forensic Snapshots and Lag Replay (`/arl snapshot`)
- Automatically records a 3D cluster scan (7x7x7 bounding box) of offending circuits when lag machines are tripped.
- Captures exact coordinates, trigger material, peak update rates, culprit player name/UUID, component breakdown, and relative block positions.
- View reports in-game or teleport directly to the snapshot site.

### Live BossBar Inspection HUD (`/arl inspect`)
- Toggleable admin diagnostic HUD that displays Chunk Updates Per Second (UPS), MSPT, and TPS in real time using an Adventure BossBar with dynamic color coding (Green, Yellow, Red) and visual dust particles.

### Integrated ClearLagg Engine (`/arl clear`)
- Fully configurable ground item and entity cleaner with multi-mode countdown broadcasts (Chat, Action Bar, Title, Subtitle).
- Supports advanced conditional rules (e.g. `Arrow onGround`, `Boat !isMounted`, `Tnt liveTime>100`).
- Pre-configured with SMP entity whitelist rules (Villagers, Iron Golems, Allays, Armor Stands, Item Frames, named mobs, tamed pets, and leashed animals are protected).
- Dropped valuables blacklist protects Netherite gear, Elytras, Totems, and Beacons.

---

## Performance

| Optimization | Technique | Benefit |
| :--- | :--- | :--- |
| **Threading Model** | Native Paper and Folia Regional Schedulers | Zero cross-region blocking and complete Folia compatibility |
| **Collections** | FastUtil Primitive Maps | Minimal memory allocations and reduced garbage collection pressure |
| **Configuration** | Okaeri Configs with Automatic `.bk` Backups | Fast, type-safe YAML serialization with data safety backups |
| **Logging** | Zero-Allocation Formatting | Background asynchronous flushing with zero main-thread overhead |

---

## Installation

1. Download the latest `.jar` from [Modrinth](https://modrinth.com/plugin/antiredstonelag) or [GitHub Releases](https://github.com/synkfr/AntiRedstoneLag/releases).
2. Place the jar file into your server's `plugins/` directory.
3. Restart your server.
4. Adjust settings in `plugins/AntiRedstoneLag/config.yml` and `messages.yml` if desired.

> **Requirements:** Paper, Purpur, or Folia **1.21.6 through 26.2** running on **Java 21+**.

---

## Commands and Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/arl help` | Show plugin command help | `antiredstonelag.use` |
| `/arl reload` | Reload configuration and messages | `antiredstonelag.reload` |
| `/arl stats` | View monitored chunks, blocks, and removal stats | `antiredstonelag.stats` |
| `/arl logs [download]` | View or prepare log files for download | `antiredstonelag.logs` |
| `/arl hotspots [page\|export]` | View or export top activity clusters | `antiredstonelag.hotspots` |
| `/arl inspect [seconds]` | Toggle real-time BossBar diagnostic HUD | `antiredstonelag.inspect` |
| `/arl snapshot [list\|view\|tp\|clear]` | View and teleport to forensic lag snapshots | `antiredstonelag.snapshot` |
| `/arl clear [items\|all\|mobs\|xp\|count\|cancel]` | Clear ground items and lag entities | `antiredstonelag.clear` |

---

## ClearLagg Engine

AntiRedstoneLag includes a conditional rule cleaner configured in `config.yml`:

```yaml
clearlagg:
  enabled: true
  interval-seconds: 300
  countdown-seconds: [60, 30, 10, 5, 4, 3, 2, 1]
  broadcast-mode: CHAT # CHAT, ACTION_BAR, TITLE, SUBTITLE, ALL, NONE
  clear-ground-items: true

  remove-entities:
    - "Arrow onGround"
    - "Spectral_Arrow onGround"
    - "Trident onGround"
    - "Snowball"
    - "Egg"
    - "Ender_Pearl"
    - "Experience_Bottle"
    - "Fireball"
    - "Small_Fireball"
    - "Dragon_Fireball"
    - "Wither_Skull"
    - "Shulker_Bullet"
    - "Boat !isMounted"
    - "Chest_Boat !isMounted"
    - "Minecart !isMounted"
    - "Minecart_Chest !isMounted"
    - "Minecart_Hopper !isMounted"
    - "Minecart_Tnt"
    - "Minecart_Furnace"
    - "Tnt liveTime>100"

  entity-whitelist:
    - "Player"
    - "Villager"
    - "Iron_Golem"
    - "Allay"
    - "Armor_Stand"
    - "Item_Frame"
    - "Glow_Item_Frame"
    - "Painting"
    - "Leash_Knot"
    - "Interaction"
    - "Text_Display"
    - "Block_Display"
    - "Item_Display"
    - "* hasName"
    - "* isTamed"
    - "* isLeashed"
    - "* isMounted"

  item-blacklist:
    - NETHER_STAR
    - BEACON
    - TOTEM_OF_UNDYING
    - ELYTRA
    - DIAMOND
    - DIAMOND_BLOCK
    - NETHERITE_INGOT
    - NETHERITE_BLOCK
    - NETHERITE_SWORD
    - NETHERITE_PICKAXE
    - NETHERITE_AXE
    - NETHERITE_SHOVEL
    - NETHERITE_HOE
    - NETHERITE_HELMET
    - NETHERITE_CHESTPLATE
    - NETHERITE_LEGGINGS
    - NETHERITE_BOOTS
```

---

## Project Disclosures and AI Policy

In accordance with Modrinth's project disclosure guidelines and content standards, the following disclosures apply to AntiRedstoneLag:

### AI Assistance Disclosure
- **Development Process**: This project was developed with the assistance of artificial intelligence tools for code refactoring, algorithm optimization, documentation structuring, and Paper/Folia API compatibility testing.
- **Human Contribution**: Architecture, logic design, integration choices, testing, debugging, and final review are directed and maintained primarily and significantly by human developers.
- **Visual Assets**: No AI-generated project icons, banners, or gallery assets are utilized. All visual materials are human-created or sourced from standard typography and game renders.

### Content Disclosures
- **Advertising / Paid Features**: None. AntiRedstoneLag contains no in-game advertisements, paid bypasses, or monetized feature gating.
- **Telemetry / Metrics**: Includes standard anonymous bStats metric collection (server version, Java version, player count). Telemetry can be disabled in `plugins/bStats/config.yml`.
- **External System Interactions**: Queries the official Modrinth API (`https://api.modrinth.com/v2/project/5UOt11Yc/version`) on startup to notify server administrators when a newer release is available. No personal or server identification data is transmitted.
- **Derivative Content**: Built as an original Paper/Folia lag prevention implementation utilizing standard open-source libraries (Okaeri Configs, FastUtil, bStats).

---

## Support and Community

- Discord: [Join Community](https://discord.gg/fGyDyp3Ak4)
- Issue Tracker: [GitHub Issues](https://github.com/synkfr/AntiRedstoneLag/issues)

---

## License

MIT License - Copyright (c) 2025-2026
