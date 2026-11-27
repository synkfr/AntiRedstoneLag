# Changelog

## Version 26.5

### Fixes
- Removed the duplicate `ConfigManager.RemovalAction` enum that silently turned `FREEZE` into `REMOVE` and deleted blocks. `FREEZE` and `DISABLE` now never break redstone blocks.
- Fixed a ClearLagg filter bug where unknown or legacy entity names such as `Minecart_Tnt` or `Boat` fell back to wildcard matching on 1.21+. Unknown entity rules now match nothing and log a warning. `Boat`, `Chest_Boat`, and legacy minecart aliases resolve to their 1.21+ Bukkit types, and default `config.yml` rules use `Chest_Minecart`, `Hopper_Minecart`, `Tnt_Minecart`, and `Furnace_Minecart`.
- Fixed Folia thread-safety issues: guarded `Bukkit.getTPS()` and `Bukkit.getAverageTickTime()`, moved `/arl inspect` player location and particles onto `player.getScheduler()`, gathered local lockdown alert recipients on the block's region thread, synchronized per-chunk owner and disabled-block maps, and cleared broken blocks on `BlockBreakEvent`.
- Fixed `LogManager.cleanupOldLogs()` deleting the newest log files instead of the oldest, initialized `logsFolder` when logging is disabled to prevent a `/arl logs` NPE, added daily log rotation, and switched file writers to UTF-8.
- Fixed `warnedPlayers` cooldowns being cleared every second, expired `freezeViolations` instead of keeping them for 24 hours, cleared `sustainedViolations` per interval, pruned pulse history maps, respected `freeze.enabled` and `adaptive.min-tps`, suppressed blank `""` messages, and replaced the hardcoded `100` performance stat with real TPS percentage on Paper.
- Fixed update checks to compare semantic version parts numerically on Modrinth release builds and updated `paper-api` (`1.21.11-R0.1-SNAPSHOT`), `bstats-bukkit` (`3.2.1`), `fastutil` (`8.5.19`), `maven-compiler-plugin` (`3.16.0`), and `maven-shade-plugin` (`3.6.2`).

### Additions
- Added `/arl redstone <on|off|notify|status>` and `removal-action: NOTIFY` so farms can run untouched while admins still get alerts, or protection can be turned off cleanly.
- Added `/arl alerts <on|off|status>`, `alerts.notify-on-tps-lag`, and `alerts.only-when-lagging` to identify which player and chunk are causing redstone lag during TPS or MSPT drops.
- Added VitePress documentation and a GitHub Pages workflow.

## Version 26.3

- Added regional entity scheduling via `entity.getScheduler().execute(...)` in `ClearLaggManager`, atomic clear tracking, and `world.getNearbyPlayers(...)` spatial lookups.

## Version 26.2

- Migrated to `paper-plugin.yml`, Paper Brigadier commands, Okaeri Configs, and Modrinth update checks.
- Added 1.21+ redstone blocks, piston velocity checks, pulse fingerprinting, `FREEZE` mode, chunk lockdown, `/arl inspect`, `/arl snapshot`, `/arl hotspots`, and `/arl clear`.

## Version 2.0.0

- Synchronized `pom.xml` version, wired `/arl stats` to `CounterManager`, switched redstone events to `LOW`, and batched async log writes.

## Version 1.2

- Initial release with chunk and block thresholds, file logging, and bStats.
