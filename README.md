![AntiRedstoneLag](https://i.postimg.cc/x1mjs7hb/minecraft-title-3.png)

<div align="center">

[![Version](https://img.shields.io/badge/version-2.0.0-blue?style=flat-square)](https://github.com/synkfr/AntiRedstoneLag/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green?style=flat-square)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-MIT-orange?style=flat-square)](LICENSE)
[![Discord](https://img.shields.io/discord/1378591879393710110?style=flat-square&label=Discord)](https://discord.gg/pAPPvSmWRK)

**High-performance redstone lag prevention for Minecraft servers**

[Features](#-features) • [Installation](#-installation) • [Configuration](#-configuration) • [Commands](#-commands) • [Support](#-support)

</div>

---

## � About

AntiRedstoneLag is a lightweight, high-performance plugin that prevents redstone-based lag machines from crashing your server. It intelligently monitors redstone activity per chunk and block, automatically handling excessive usage while allowing normal redstone builds (farms, doors, etc.) to function normally.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| **Smart Detection** | Monitors redstone updates per chunk and block with configurable thresholds |
| **Multiple Actions** | Choose to remove, disable, or drop problematic redstone |
| **Warning System** | Warns players before their redstone is removed |
| **Bypass Permission** | Allow trusted players to bypass limits |
| **Whitelist Mode** | Only monitor specific chunks instead of all |
| **Persistent Stats** | Statistics survive server restarts |
| **Update Checker** | Get notified when new versions are available |
| **Hot Reload** | Apply config changes without restart |
| **World Control** | Enable/disable per world |
| **Advanced Logging** | File-based logging with rotation |

---

## 📦 Installation

1. Download the latest release from [Releases](https://modrinth.com/plugin/antiredstonelag)
2. Place the `.jar` in your server's `plugins/` folder
3. Restart your server
4. Configure in `plugins/AntiRedstoneLag/config.yml`

**Requirements:** Paper/Spigot 1.21+ (Java 21)

---

## ⚙️ Configuration

<details>
<summary><b>Click to expand full config.yml</b></summary>

```yaml
# Thresholds
chunk-threshold: 500      # Max redstone updates per chunk per reset
block-threshold: 15       # Max updates per block before action

# Reset interval in ticks (20 = 1 second)
reset-interval-ticks: 20

# Debug mode for troubleshooting
debug: false

# Action when threshold exceeded: REMOVE, DISABLE, or DROP
removal-action: REMOVE

# Warning system
warning:
  enabled: true
  threshold-percent: 80   # Warn at 80% of threshold

# Worlds to monitor (use * for all)
enabled-worlds:
  - "*"

# Whitelist mode - only monitor specific chunks
whitelist:
  enabled: false
  chunks:
    - "world:0:0"

# Alerts
alerts:
  enabled: true
  log-to-console: true

# Logging
logging:
  enabled: true
  console-mirror: false
  max-files: 10
  max-size-mb: 10
  performance-stats: true

# Monitored components
redstone-components:
  - REDSTONE_WIRE
  - REPEATER
  - COMPARATOR
  - OBSERVER
  - PISTON
  - STICKY_PISTON
  - REDSTONE_TORCH
  - REDSTONE_WALL_TORCH
  - LEVER
  - DAYLIGHT_DETECTOR
  - TARGET
  - TRAPPED_CHEST
  - DROPPER
  - DISPENSER
  - HOPPER
```

</details>

### Key Options

| Option | Default | Description |
|--------|---------|-------------|
| `chunk-threshold` | 500 | Max redstone updates per chunk per reset interval |
| `block-threshold` | 15 | Max updates per block before considered a lag machine |
| `removal-action` | REMOVE | Action to take: `REMOVE`, `DISABLE`, or `DROP` |
| `warning.enabled` | true | Warn players before removing their redstone |
| `whitelist.enabled` | false | Only monitor whitelisted chunks |

---

## 💻 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/arl help` | Show help message | `antiredstonelag.use` |
| `/arl reload` | Reload configuration | `antiredstonelag.reload` |
| `/arl stats` | View statistics | `antiredstonelag.stats` |
| `/arl logs` | View logging info | `antiredstonelag.logs` |
| `/arl logs download` | Get latest log file info | `antiredstonelag.logs` |

---

## 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `antiredstonelag.use` | Use basic commands | true |
| `antiredstonelag.reload` | Reload configuration | op |
| `antiredstonelag.stats` | View statistics | op |
| `antiredstonelag.logs` | Access logs | op |
| `antiredstonelag.alerts` | Receive removal alerts | op |
| `antiredstonelag.bypass` | Bypass redstone limits | false |
| `antiredstonelag.admin` | Receive update notifications | op |

---

## 🔔 Alert Example

When a lag machine is detected:

```
┌─ ⚠ Lag Machine Detected ─────────┐
│ Coordinates: 123, 64, 456        │
│ World: world                     │
│ Block: REDSTONE_WIRE             │
│ Chunk: 501 │ Block: 16           │
└─ Removed for server performance ─┘
```

---

## 🧩 Compatibility

| Platform | Status |
|----------|--------|
| Paper 1.21+ | ✅ Supported |
| Spigot 1.21+ | ✅ Supported |
| Purpur 1.21+ | ✅ Supported |
| Folia | ❌ Not yet |

---

## � Performance

AntiRedstoneLag is optimized for minimal server impact:

- **Primitive collections** (fastutil) to reduce memory overhead
- **Batched log writes** to minimize I/O
- **Cached config values** to avoid repeated lookups
- **String keys** instead of object keys to prevent memory leaks
- **Lazy Location creation** only when needed

---

## ❓ Support

[![Discord](https://img.shields.io/discord/1378591879393710110?style=for-the-badge&logo=discord&label=Discord)](https://discord.gg/pAPPvSmWRK)
[![GitHub Issues](https://img.shields.io/github/issues/synkfr/AntiRedstoneLag?style=for-the-badge&logo=github)](https://github.com/synkfr/AntiRedstoneLag/issues)

---

## 📜 License

MIT License - Copyright 2025 AyoSynk

