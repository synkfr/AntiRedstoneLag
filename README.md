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
- ♻️ **Hot Reload Support** — Instantly apply config changes with `/arl-reload`.

---

## 🛠️ Configuration

Open the `config.yml` file to fine-tune the plugin:

```yaml
# Maximum redstone updates per chunk per second
# if you don't know what this means don't touch it
chunk-threshold: 500

# Updates per block before being considered a lag machine
# if you don't know what this means don't touch it
block-threshold: 15

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

Use `/arl-reload` in-game or via console to apply config changes without restarting the server.

---

## 📘 Usage

* ✅ Edit `config.yml` to:

    * Adjust performance thresholds
    * Include/exclude redstone component types
* 🔄 Apply changes using:

  ```bash
  /arl-reload
  ```

---

## 💡 Example Use Case

A survival server might have player who builds lag-machine without permissions might experience lag spikes due to excessive redstone activity. With **Advanced Redstone Limiter**, you can:

* Cap redstone ticks per second
* Target only specific block types (e.g., `REPEATER`, `REDSTONE_WIRE`)
* Safely auto-disable overactive components

---

## 🧩 Plugin Compatibility

✔️ Supports Paper, Spigot, Purpur, and compatible forks.

---

## 📜 License

LandClaim is licensed under the MIT License.

```license
Copyright 2023 AyoSynk

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

## 🤝 Support & Contributions

Found a bug or want to suggest a feature?
Feel free to open an [issue](https://github.com/your-repo/issues) or submit a pull request.

---
