# Changelog

## Version 26.5

### Fixes
- Removed the duplicate `ConfigManager.RemovalAction` enum that mapped `FREEZE` to `REMOVE`. `FREEZE` and `DISABLE` no longer break redstone blocks.
- Fixed ClearLagg matching all unwhitelisted mobs when an unknown or legacy `EntityType` name appeared in `remove-entities`. Added `Boat` and `Chest_Boat` group support, legacy minecart aliases, and updated 1.21+ defaults.
- Fixed Folia thread-safety violations in `/arl inspect`, TPS checks, lockdown broadcasts, and chunk owner/disabled maps.
- Fixed `LogManager.cleanupOldLogs()` deleting newest logs instead of oldest, fixed `/arl logs` NPE when logging is disabled, added daily rotation, and enforced UTF-8 writes.
- Fixed 1-second `warnedPlayers` wipe, 24-hour `freezeViolations` stickiness, `sustainedViolations` accumulation across hours, pulse history growth, blank message emission, and hardcoded `/arl stats` performance.
- Added semantic version comparison for Modrinth release checks and updated Maven dependencies.

### Additions
- Added `/arl redstone <on|off|notify|status>` and `removal-action: NOTIFY`.
- Added `/arl alerts <on|off|status>`, `alerts.notify-on-tps-lag`, and `alerts.only-when-lagging`.
- Added VitePress documentation and GitHub Pages deployment.

## Version 26.3

- Added Folia `entity.getScheduler().execute(...)` handling in `ClearLaggManager`, atomic clear tracking, and spatial nearby-player lookups.

## Version 26.2

- Migrated to `paper-plugin.yml`, Brigadier commands, Okaeri Configs, and Modrinth updates.
- Added 1.21+ components, piston checks, pulse fingerprinting, `FREEZE` mode, chunk lockdown, `/arl inspect`, `/arl snapshot`, `/arl hotspots`, and `/arl clear`.
