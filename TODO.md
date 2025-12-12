# AntiRedstoneLag - TODO List

## 🐛 Bugs

- [x] **Version mismatch** - `pom.xml` version is `1.2` but `plugin.yml` declares `2.0.0` ✅ Fixed in v2.0.0
- [x] **Unused variable in RedstoneListener** - `materialName` is stored but never used (line 48) ✅ Fixed in v2.0.0
- [x] **Stats command shows hardcoded values** - `statsCommand()` in `CommandHandler` always shows `0` for all stats instead of actual values from `CounterManager` ✅ Fixed in v2.0.0
- [x] **Log download not implemented** - `provideLogFile()` method returns "not implemented yet" message ✅ Fixed in v2.0.0 (shows log file info)
- [x] **Potential NPE in CounterManager.sendAlert()** - `location.getWorld()` could return null in unloaded chunks ✅ Fixed in v2.0.0
- [x] **LogManager truncated read** - Need to verify `processLogQueue()` method implementation (file was truncated) ✅ Verified in v2.0.0 (implementation complete)
- [x] **Unused imports** - `java.io.File` and `java.nio.file.Files` imported in `CommandHandler` but `Files` is never used ✅ Fixed in v2.0.0

## ⚡ Optimizations

- [x] **Use ConcurrentHashMap for counters** - `CounterManager` uses regular `HashMap` for `chunkCounters` and `blockCounters` which could cause issues with async access ✅ Fixed in v2.0.0
- [x] **Location as map key is inefficient** - `Location` objects are mutable and expensive as HashMap keys; consider using `BlockVector` or a custom immutable key class ✅ Fixed in v2.0.0 (using String keys)
- [x] **Chunk object as map key** - Using `Chunk` directly as key can cause memory leaks; use `ChunkKey` (world + x + z) instead ✅ Fixed in v2.0.0 (using String keys)
- [x] **Reduce object allocation in hot path** - `RedstoneListener.onRedstoneUpdate()` creates new `Location` object on every redstone event via `block.getLocation()` ✅ Fixed in v2.0.0 (Location only created when needed)
- [x] **Batch log writes** - `LogManager` writes to file frequently; consider buffering more aggressively ✅ Fixed in v2.0.0 (batch flush every 50 entries or 500ms)
- [x] **Use primitive collections** - Consider using primitive-specialized collections (e.g., fastutil) for counters to reduce boxing overhead ✅ Added in v2.0.0 (Object2IntOpenHashMap)
- [x] **Event priority should be LOW/LOWEST** - Using `MONITOR` priority but modifying block state; should use `LOW` or `LOWEST` to allow other plugins to react ✅ Fixed in v2.0.0
- [x] **Cache config values in RedstoneListener** - Currently calls `configManager.getRedstoneMaterials()` and `configManager.isWorldEnabled()` on every event ✅ Fixed in v2.0.0

## 🔧 Improvements

- [x] **Inject CounterManager into CommandHandler** - `statsCommand()` needs access to `CounterManager` to show real statistics ✅ Fixed in v2.0.0
- [x] **Add bypass permission** - Allow players with `antiredstonelag.bypass` to place redstone without limits ✅ Added in v2.0.0
- [x] **Configurable reset interval** - Currently hardcoded to 1 second (20 ticks); make configurable ✅ Added in v2.0.0
- [x] **Add cooldown for alerts** - Prevent alert spam when multiple blocks are removed in quick succession ✅ Added in v2.0.0
- [x] **Improve log rotation** - Current rotation only checks on cleanup task; should also check on write ✅ Fixed in v2.0.0 (checks on flush)
- [x] **Configurable removal action** - Instead of just removing to AIR, allow: disable, drop item, notify owner ✅ Added in v2.0.0
- [x] **Add whitelist mode** - Option to only monitor specific chunks/regions instead of blacklist approach ✅ Added in v2.0.0
- [x] **Persist statistics** - Save `totalRemovals` and `removalsToday` to file so they survive restarts ✅ Added in v2.0.0
- [x] **Add update checker** - Notify admins when a new version is available ✅ Added in v2.0.0 (SpigotMC API)
- [x] **Better error handling in ConfigManager** - Validate threshold values (e.g., must be positive) ✅ Added in v2.0.0
- [x] **Add debug mode** - Verbose logging option for troubleshooting ✅ Added in v2.0.0

## ✨ Additional Features

- [ ] **Web dashboard** - Real-time monitoring dashboard via embedded web server or external service
- [ ] **Discord webhook integration** - Send alerts to Discord channel
- [ ] **Player tracking** - Track which player placed the removed redstone (requires block logging integration)
- [ ] **Heatmap visualization** - Generate chunk heatmaps showing redstone activity
- [ ] **Auto-adjustment mode** - Dynamically adjust thresholds based on server TPS
- [ ] **Scheduled restrictions** - Different thresholds for peak hours vs off-peak
- [ ] **Per-player limits** - Individual redstone limits per player/permission group
- [ ] **Rollback support** - Integration with CoreProtect/Prism to restore removed blocks
- [ ] **GUI configuration** - In-game GUI for adjusting settings
- [ ] **Export/import configs** - Share configurations between servers
- [ ] **Redstone profiler command** - `/arl profile <chunk>` to analyze redstone activity in specific chunk
- [x] **Warning system** - Warn players before removing their redstone (configurable threshold) ✅ Added in v2.0.0
- [ ] **Block protection** - Allow players to "protect" certain redstone builds from removal (with limits)
- [ ] **API for developers** - Events and methods for other plugins to interact with
- [ ] **Folia support** - Compatibility with Folia's regionized multithreading
- [ ] **Bedrock support** - Geyser/Floodgate compatibility testing

## 📝 Code Quality

- [ ] **Add unit tests** - No test coverage currently
- [x] **Add JavaDoc comments** - Document public methods and classes ✅ Added in v2.0.0 (key classes documented)
- [ ] **Consistent naming** - Some methods use camelCase, config uses kebab-case inconsistently
- [x] **Extract constants** - Magic numbers like `20L * 60 * 60` should be named constants ✅ Fixed in v2.0.0
- [x] **Use dependency injection** - Consider using a DI framework or manual constructor injection consistently ✅ Improved in v2.0.0
- [x] **Add null checks** - Several methods don't validate input parameters ✅ Added in v2.0.0
- [ ] **Separate concerns** - `CounterManager` handles both counting and alerting; consider splitting

## 📚 Documentation

- [ ] **Add CONTRIBUTING.md** - Guidelines for contributors
- [x] **Add CHANGELOG.md** - Track version changes ✅ Added UPDATES.md in v2.0.0
- [x] **Improve config comments** - More detailed explanations in config.yml ✅ Updated in v2.0.0
- [ ] **Add wiki pages** - Detailed setup and configuration guides
- [ ] **API documentation** - If API is added, document it properly
