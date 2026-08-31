# AntiRedstoneLag - Changelog

## Version 26.2

### Major Architecture and Modernization
- **Native Paper Plugin Migration** - Fully converted to `paper-plugin.yml` and Paper 1.21+ Lifecycle Brigadier Command API.
- **Folia Multi-Threading Support** - Complete regional threading compatibility using `Bukkit.getGlobalRegionScheduler()`, `Bukkit.getAsyncScheduler()`, and `Bukkit.getRegionScheduler().execute(...)` with zero cross-region blocking.
- **Okaeri Configs Engine** - Migrated configuration (`config.yml`) and message handling (`messages.yml`) to Okaeri Configs with automatic `.bk` safety backups on startup.
- **Modrinth Integration** - Updated the update notifier to use the official Modrinth API (`project ID: 5UOt11Yc`) with non-blocking asynchronous checks.
- **Contributor Attribution** - Merged performance optimizations and hotspot clustering from PR #2 with official contributor credit to `paradoxnafi`.

### Minecraft 1.21+ Component Support
- **Modern Redstone Components** - Added monitoring support for Crafters (`Material.CRAFTER`), Copper Bulbs (all oxidation and waxed states), Calibrated Sculk Sensors, and Chiseled Bookshelves.
- **Piston Velocity and 0-Tick Interception** - Added `BlockPistonExtendEvent` and `BlockPistonRetractEvent` listeners to catch 0-tick loops and rapid push/pull lag engines.

### Intelligent Detection and Farm Protection
- **Periodicity Clock Fingerprinting** - Analyzes interval deltas and variance between consecutive pulses to distinguish true lag machines from bursty contraptions.
- **100% Farm Immunity** - Implemented Sustained Consecutive Violation Detection to ensure sugarcane farms, iron farms, melon/pumpkin harvesters, and hopper sorting halls never trigger false removals.
- **Adaptive Performance Scaling** - Dynamically scales thresholds based on Paper's real-time average tick time (`Bukkit.getAverageTickTime()`) and TPS, providing 1.5x headroom during healthy performance and tightening under load.
- **Player Proximity Throttling** - Enforces stricter limits on unattended contraptions running in chunkloaded areas without nearby players.

### Non-Destructive Mitigation
- **Tiered Freezing and Auto-Resume** - Added non-destructive `FREEZE` as the default removal action, pause-throttling high-frequency clocks for a configurable duration (default: 15s) before escalating to block removal.
- **Chunk Lockdown (EMP)** - Temporarily disables all redstone activity in a chunk for a configurable duration when severe lag machines are tripped.

### Diagnostic Tools and Visuals
- **Live Adventure BossBar Inspector (`/arl inspect`)** - Real-time BossBar HUD displaying Chunk UPS, MSPT, and Server TPS with dynamic color grading (Green, Yellow, Red) and visual particle trails.
- **3D Forensic Snapshots and Lag Replay (`/arl snapshot`)** - Automatically records 3D bounding box scans (7x7x7), trigger metrics, culprit player name/UUID, and component breakdown upon detecting lag machines. Includes in-game reports and direct teleportation (`/arl snapshot tp`).
- **Hotspot Cluster Analysis (`/arl hotspots`)** - Adjacent-chunk BFS clustering with activity-weighted centers, interactive teleport suggestions, and file export (`/arl hotspots export`).

### Integrated ClearLagg Engine (`/arl clear`)
- **Folia-Safe Entity Cleaner** - Ground item and entity cleanup with support for manual and automatic scheduled clears.
- **Conditional Entity Rules** - Rule parser supporting tags such as `onGround`, `!isMounted`, `hasName`, `isTamed`, `isLeashed`, `isBaby`, `liveTime>X`, and `inWater`.
- **SMP Entity Whitelist** - Default immunity for Villagers, Iron Golems, Allays, Armor Stands, Item Frames, Display entities, named mobs, tamed pets, and leashed animals.
- **Valuable Item Blacklist** - Spares dropped Netherite gear, Elytras, Totems of Undying, Diamonds, and Beacons from deletion.
- **Multi-Mode Countdown Broadcasts** - Configurable countdown warnings supporting Chat, Action Bar, Title, Subtitle, All, or None.

### Bug Fixes and Usability
- **Fixed Hex Color Formatting** - Resolved issue where `&#RRGGBB` color codes rendered as raw text in chat and console.
- **Eliminated Notification and Snapshot Spam** - Added a 60-second snapshot cooldown per block and instant signal cancellation on frozen components.
- **Smart Culprit Resolution** - Added nearby player proximity scanning (within 48 blocks) when the original block placer is not in memory.
- **Fixed Brigadier Tab Completion** - Full support for empty and partial argument completion across all subcommands.

---

## Version 2.0.0

### Bug Fixes
- **Fixed version mismatch** - Synchronized `pom.xml` version with `plugin.yml`.
- **Fixed stats command** - `/arl stats` shows real statistics from `CounterManager`.
- **Fixed potential NPE** - Added null check for `location.getWorld()` in `CounterManager.sendAlert()`.
- **Fixed event priority** - Changed to `LOW` priority for proper block state management.

### Optimizations
- **Thread-safe counters** - Replaced maps with `ConcurrentHashMap` and FastUtil collections.
- **Memory-efficient keys** - Using packed coordinate bitmasks instead of Location objects.
- **Batched log writes** - Buffered log entries flushed asynchronously to prevent main-thread lag.

---

## Version 1.2

### Features
- Initial release with chunk-based redstone monitoring.
- Block-level threshold detection.
- File-based logging with rotation.
- bStats metrics integration.
