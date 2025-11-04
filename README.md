![AntiRedstonelag plguin title](https://i.postimg.cc/x1mjs7hb/minecraft-title-3.png)
# ⚙️ Advanced Redstone Limiter

A modular, high-performance plugin designed to manage redstone activity on your Minecraft server. Prevent lag, enhance server stability, and fine-tune redstone mechanics with precision. Normal redstone auto-mated builds like gold farm, iron farm, etc will still work.

--- 

## 📦 Installation

1. **Build** the plugin by compiling the provided classes into a `.jar` file.
2. **Place** the `.jar` in your server's `plugins/` directory.
3. **Restart** or **start** the server.

---

## 🚀 Features

- ✅ **Modular Design** — Update or extend easily without modifying the core logic.
- 🎚️ **Configurable Thresholds** — Set redstone usage limits that suit your server's needs.
- 🔄 **Auto Counter Reset** — All redstone counters reset every second for real-time control.
- 🎯 **Selective Monitoring** — Monitor specific redstone components only.
- 🧱 **Safe Block Removal** — Prevent physics updates during automatic redstone cleanup.
- ♻️ **Hot Reload Support** — Instantly apply config changes with `/arl reload`.
- 📢 **Clean Alert System** — Beautiful, formatted alerts with coordinates when redstone is removed.
- 📝 **Advanced Logging** — File-based logging system with optional console mirroring.
- 🌍 **World-Specific Control** — Enable or disable the plugin per world.
- 📊 **Statistics Tracking** — View removal statistics and performance metrics.

---
## 🔍 Preview
<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/PMqz25ctRlU" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

## 🛠️ Configuration

Open the `config.yml` file to fine-tune the plugin:

```yaml
# Maximum redstone updates per chunk per second
# if you don't know what this means don't touch it
chunk-threshold: 500

# Updates per block before being considered a lag machine
# if you don't know what this means don't touch it
block-threshold: 15

# Worlds where the plugin is active (use * for all worlds)
enabled-worlds:
  - "*"

# Alert settings
alerts:
  enabled: true
  log-to-console: true

# Advanced logging system
logging:
  enabled: true
  console-mirror: false  # Set to true to mirror file logs to console
  max-files: 10
  max-size-mb: 10
  performance-stats: true

# Redstone components to monitor
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

### Configuration Options

- **`chunk-threshold`**: Maximum redstone updates per chunk per second before triggering removal
- **`block-threshold`**: Maximum updates per individual block before being considered a lag machine
- **`enabled-worlds`**: List of worlds where the plugin is active (use `["*"]` for all worlds)
- **`alerts.enabled`**: Enable/disable alert notifications to players
- **`alerts.log-to-console`**: Send alert messages to console
- **`logging.enabled`**: Enable file-based logging system
- **`logging.console-mirror`**: Mirror all file logs to console (default: false to prevent spam)
- **`logging.max-files`**: Maximum number of log files to keep
- **`logging.max-size-mb`**: Maximum size per log file in megabytes
- **`logging.performance-stats`**: Log performance statistics periodically

Use `/arl reload` in-game or via console to apply config changes without restarting the server.

---

## 📘 Usage

### Commands

- `/arl reload` - Reload configuration and messages
- `/arl stats` - View plugin statistics (total removals, chunks monitored, etc.)
- `/arl logs` - View logging system information
- `/arl logs download` - Download the latest log file
- `/arl help` - Show help message

### Permissions

- `antiredstonelag.use` - Use basic plugin commands (default: true)
- `antiredstonelag.reload` - Reload configuration (default: op)
- `antiredstonelag.stats` - View statistics (default: op)
- `antiredstonelag.logs` - Access logging system (default: op)
- `antiredstonelag.alerts` - Receive redstone removal alerts (default: op)

### Configuration

* ✅ Edit `config.yml` to:
    * Adjust performance thresholds
    * Include/exclude redstone component types
    * Configure world-specific settings
    * Enable/disable alerts and logging
* 🔄 Apply changes using: `/arl reload`

---

## 💡 Example Use Case

A survival server might have players who build lag-machines without permissions, causing lag spikes due to excessive redstone activity. With **Advanced Redstone Limiter**, you can:

* Cap redstone ticks per second
* Target only specific block types (e.g., `REPEATER`, `REDSTONE_WIRE`)
* Safely auto-disable overactive components
* Receive clean, formatted alerts with coordinates when redstone is removed
* Track removal statistics and monitor performance

### Alert Format

When a lag machine is detected and removed, players with the `antiredstonelag.alerts` permission will receive a beautifully formatted alert:

```
┌─ ⚠ Lag Machine Detected ─┐
│ Coordinates: 123, 64, 456 │
│ World: world_name │
│ Block: REDSTONE_WIRE │
│ Chunk Updates: 501 │ Block Updates: 16 │
└─ Removed for server performance ─┘
```

The coordinates make it easy to locate and investigate the removed redstone device.

---

## 🧩 Plugin Compatibility

✔️ Supports Paper, Spigot, Purpur, and compatible forks.

---

## 📜 License

AntiRedstoneLag is licensed under the MIT License.

```license
Copyright 2025 AyoSynk

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
---

## ❓ Support

For support or bug reports:  
[![Discord](https://img.shields.io/discord/1378591879393710110?style=for-the-badge)](https://discord.gg/pAPPvSmWRK)  
[![GitHub Issues](https://img.shields.io/github/issues/synkfr/AntiRedstoneLag?style=for-the-badge)](https://github.com/synkfr/AntiRedstoneLag/issues)

---
