# Changelog

## Version 2.0.0

### Architecture & Upgrade
- Full modular rewrite targeting Java 21 and Paper/Purpur/Folia 1.21.6+ (`api-version: '1.21'`).
- Added automatic v1 → v2 safety backup (`config.yml.bk` and `messages.yml.bk`), compatible v1 `config.yml` migration, and legacy `stats.yml` migration.
- Rebuilt `messages.yml` and all command/GUI templates using clean MiniMessage hex styling (`#00E262`, `#E20000`, `#E2B700`, `#A0A0A0`, `#FFFFFF`, `#1F1F1F`), removing hardcoded chat strings and legacy ASCII boxes.
- Switched `/arl` to native Paper Brigadier command registration (`ArlCommandTree`) and added cancellable `TaskHandle` scheduling so `/arl reload` resyncs interval timers cleanly.

### Additions
- Added interactive Staff Inventory GUI (`/arl gui` / `/arl menu`) with 5 screens: Dashboard, Hotspots Browser, Snapshots Browser, Frozen & Locked Regions, and ClearLagg Console.
- Added persistent SQLite storage (with atomic Gson JSON fallback) for block ownership across chunk unloads/restarts, persisted forensic snapshots, and lifetime statistics.
- Added `/arl unfreeze <all|here>`, `/arl unlock <all|here>`, `/arl whitelist <add|remove|list>`, `/arl snapshot highlight|delete`, `/arl logs tail`, and `/arl clear pause|resume`.
- Upgraded `ClockFingerprinter` to a circular pulse ring buffer detecting steady and alternating multi-phase clocks, added two-interval burst protection so simple vanilla SMP farms do not false-flag, tuned SMP-safe defaults (`chunk-threshold: 800`, `block-threshold: 30`, `piston-loop-threshold: 16`, `max-minecarts-per-block: 16`, farm minecart/boat immunity), and added per-world overrides (`worlds.<name>`).
- Added in-world block highlight particles, chunk border markers, and ActionBar indicators to `/arl inspect`, plus `clock-resumed` owner alerts when freezes expire.
- Added ClearLagg minimum item age protection, optional chunk entity density caps, and accurate async completion counts (`CompletableFuture<ClearResult>`).
- Added optional PlaceholderAPI expansion (`%arl_*%`) and async Discord Webhook alerts.

## Version 26.5

### Fixes
- Removed duplicate `RemovalAction` enum and fixed Folia thread safety, ClearLagg legacy aliases, log rotation, and performance stats reporting.

### Additions
- Added `/arl redstone <on|off|notify|status>`, `/arl alerts <on|off|status>`, `alerts.notify-on-tps-lag`, `alerts.only-when-lagging`, and VitePress docs.
