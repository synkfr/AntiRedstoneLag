![AntiRedstoneLag](https://i.postimg.cc/x1mjs7hb/minecraft-title-3.png)

<div align="center">

[![Version](https://img.shields.io/badge/version-2.1.0-blue?style=for-the-badge&logo=github)](https://github.com/synkfr/AntiRedstoneLag/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green?style=for-the-badge&logo=minecraft)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-MIT-orange?style=for-the-badge)](LICENSE)
[![Discord](https://img.shields.io/discord/1378591879393710110?style=for-the-badge&logo=discord&label=Discord)](https://discord.gg/fGyDyp3Ak4)

**The Ultimate High-Performance Redstone Lag Prevention for Modern Minecraft Servers**

[Features](#-features) • [Performance Specs](#-performance) • [Installation](#-installation) • [Commands](#-commands) • [Developer Builds](#-automated-builds)

</div>

---

## ⚡ About AntiRedstoneLag

AntiRedstoneLag is a lightweight yet extremely powerful plugin designed to safeguard your server from redstone-based lag machines and accidental over-engineering. Unlike basic limiters, AntiRedstoneLag uses **asynchronous logging** and **O(1) tracking structures** to monitor redstone activity with zero impact on your server's tick rate.

---

## ✨ Features

- 🚀 **Asynchronous Foundation**: Logging and timestamp generation are offloaded to background threads.
- 🎯 **O(1) Chunk Cleanup**: Refactored tracking logic for instant cleanup during chunk unloading.
- 🌈 **Modern UI**: Full migration to **Adventure API** and **MiniMessage** for beautiful, hex-color alerts.
- 🛡️ **Lag Guard 1.21**: Specifically optimized for Paper 1.21+ physics and redstone handling.
- ⚙️ **Smart Actions**: Choose between `REMOVE` (with physics notifications), `DROP`, or `DISABLE`.
- 📊 **Persistent Analytics**: Statistics tracking that survives server restarts.
- 🔄 **Automated CI/CD**: Development builds published automatically via GitHub Actions.

---

## 🚀 Performance Specs

| Optimization | Method | Impact |
| :--- | :--- | :--- |
| **Tracking** | Nested Concurrent Maps | **O(1)** Chunk Cleanup |
| **Messaging** | Adventure API | Hex & Gradient Support |
| **Logging** | Async Timestamping | Zero Main-Thread Overhead |
| **Memory** | Fastutil Collections | Reduced Boxing/GC Pressure |

---

## 📦 Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/antiredstonelag) or [GitHub](https://github.com/synkfr/AntiRedstoneLag/releases).
2. Place the `.jar` in your server's `plugins/` folder.
3. Restart your server.
4. Configure limits in `plugins/AntiRedstoneLag/config.yml`.

> [!IMPORTANT]
> **Requirements:** Paper, Spigot, or Purpur **1.21.1+** and **Java 21**.

---

## 🛠️ Configuration

Modern power and simplicity. AntiRedstoneLag supports **MiniMessage** hex codes (`<gold>`, `<#ffcc00>`, etc.) in all messages.

<details>
<summary><b>View Optimized config.yml</b></summary>

```yaml
# Thresholds
chunk-threshold: 500      # Max redstone updates per chunk per resetting interval
block-threshold: 15       # Max updates per individual block before action

# Reset interval in ticks (20 = 1 second)
reset-interval-ticks: 20

# Action when threshold exceeded: REMOVE, DISABLE, or DROP
# REMOVE: Clears block (Recommended)
# DISABLE: Cancels active signals
# DROP: Breaks block and drops items
removal-action: REMOVE

# Warning system
warning:
  enabled: true
  threshold-percent: 80   # Warn at 80% of threshold

# Alerts
alerts:
  enabled: true
  log-to-console: true

# Logging
logging:
  enabled: true
  performance-stats: true
```
</details>

---

## 💻 Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/arl help` | View command help | `antiredstonelag.use` |
| `/arl reload` | Hot-reload config | `antiredstonelag.reload` |
| `/arl stats` | Real-time performance stats | `antiredstonelag.stats` |
| `/arl logs` | View removal history | `antiredstonelag.logs` |

---

## 🏗️ Automated Builds

We use GitHub Actions to ensure code quality and instant delivery. Every push to `master` triggers a development build.

> [!TIP]
> **Beta Testing:** Want the latest fixes before a stable release? Download the latest **Development Pre-release** from the [Repository Actions](https://github.com/synkfr/AntiRedstoneLag/actions) or the [Releases](https://github.com/synkfr/AntiRedstoneLag/releases) tab (labeled with `dev-bX`).

---

## ❓ Support

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-7289DA?style=for-the-badge&logo=discord)](https://discord.gg/fGyDyp3Ak4)
[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues-red?style=for-the-badge&logo=github)](https://github.com/synkfr/AntiRedstoneLag/issues)

---

## 📜 License

MIT License - Copyright © 2025 AyoSynk. Developed with ❤️ for the Minecraft High-Performance Community.
