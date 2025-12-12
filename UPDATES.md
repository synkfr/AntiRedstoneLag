# AntiRedstoneLag - Changelog

## Version 2.0.0

### Bug Fixes
- **Fixed version mismatch** - Synchronized `pom.xml` version with `plugin.yml` (both now 2.0.0)
- **Fixed unused variable** - Removed unused `materialName` variable in `RedstoneListener`
- **Fixed stats command** - `/arl stats` now shows real statistics from `CounterManager` instead of hardcoded zeros
- **Fixed unused imports** - Removed unused imports from `CommandHandler` and `RedstoneListener`
- **Fixed potential NPE** - Added null check for `location.getWorld()` in `CounterManager.sendAlert()` to prevent crashes with unloaded chunks
- **Fixed event priority** - Changed from `MONITOR` to `LOW` priority since we modify block state
- **Fixed log download** - `/arl logs download` now shows log file info (name, size, path, modified date)

### Optimizations
- **Thread-safe counters** - Replaced `HashMap` with `ConcurrentHashMap` and `AtomicInteger` for thread safety
- **Memory-efficient keys** - Using String keys instead of `Chunk`/`Location` objects to prevent memory leaks
- **Extracted time constants** - Magic numbers replaced with named constants (`TICKS_PER_HOUR`, `TICKS_PER_DAY`, `DAY_MS`, `ALERT_COOLDOWN_MS`, `BATCH_SIZE`, `FLUSH_INTERVAL_MS`)
- **Cached config values** - `RedstoneListener` now caches redstone materials, enabled worlds, and removal action for better performance
- **Refresh cache on reload** - Cached values are automatically refreshed when config is reloaded
- **Reduced object allocation** - Avoid creating `Location` objects in hot path; only create when actually needed for logging
- **Batched log writes** - Log entries are buffered and flushed every 50 entries or 500ms instead of on every write
- **Improved log rotation** - Log file size is now checked on every flush, not just during cleanup task
- **Primitive collections (fastutil)** - Replaced `ConcurrentHashMap<String, AtomicInteger>` with `Object2IntOpenHashMap` to eliminate boxing overhead and reduce memory allocations

### New Features
- **Bypass permission** - Players with `antiredstonelag.bypass` permission can place redstone without limits
- **Block owner tracking** - Tracks who placed redstone blocks for bypass permission checks
- **Configurable reset interval** - New `reset-interval-ticks` config option (default: 20 ticks = 1 second)
- **Debug mode** - New `debug` config option for verbose logging during troubleshooting
- **Config validation** - Threshold values are now validated (must be positive)
- **Alert cooldown** - 1 second cooldown between alerts to prevent spam when multiple blocks are removed
- **Persistent statistics** - Statistics (`totalRemovals`, `removalsToday`) are saved to `stats.yml` and survive server restarts
- **Configurable removal action** - New `removal-action` config option with three modes:
  - `REMOVE` - Set block to air (default)
  - `DISABLE` - Cancel redstone signal without removing block
  - `DROP` - Break block and drop item
- **Warning system** - Warns players when their redstone activity approaches the threshold
  - Configurable warning threshold (default: 80% of limit)
  - Per-player cooldown to prevent spam
  - Only warns block owners who are online
- **Update checker** - Automatically checks for new versions on SpigotMC
  - Notifies admins with `antiredstonelag.admin` permission on join
  - Non-blocking async check on startup
- **Whitelist mode** - Only monitor specific chunks instead of all chunks
  - Configure chunks as `world:chunkX:chunkZ` format
  - Useful for monitoring only known problem areas

### Improvements
- **Real-time statistics** - Stats command now displays actual monitored chunks, blocks, total removals, and daily removals
- **Better code structure** - `CommandHandler` now receives `CounterManager` via dependency injection
- **Improved config comments** - Better documentation in `config.yml`
- **Stats saved on shutdown** - Statistics are automatically saved when the plugin is disabled
- **Null safety** - Added null checks to `CounterManager` methods to prevent potential NPEs
- **JavaDoc comments** - Added documentation to key methods

### New Permissions
- `antiredstonelag.bypass` - Bypass redstone limits (default: false)

---

## Version 1.2

### Features
- Initial premium release
- Chunk-based redstone monitoring
- Block-level threshold detection
- Configurable redstone component list
- World-specific enable/disable
- Alert system with hex color support
- File-based logging with rotation
- bStats metrics integration
- Tab completion for commands

### Commands
- `/arl reload` - Reload configuration
- `/arl stats` - View statistics
- `/arl logs` - View logging info
- `/arl help` - Show help

### Permissions
- `antiredstonelag.use` - Basic commands
- `antiredstonelag.reload` - Reload config
- `antiredstonelag.stats` - View stats
- `antiredstonelag.logs` - Access logs
- `antiredstonelag.alerts` - Receive alerts
