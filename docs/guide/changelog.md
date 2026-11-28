# Changelog

## Version 2.0.0

- Full modular rewrite targeting Paper/Purpur/Folia 1.21.6+ and Java 21.
- Added safe v1 → v2 backup (`config.yml.bk` and `messages.yml.bk`) with automatic setting migration and MiniMessage redesign.
- Added Staff Inventory GUI (`/arl gui`), persistent SQLite/JSON storage, multi-pattern clock fingerprinting, SMP-safe two-interval farm protection, piston/crafter/hopper/minecart detectors, visual particle inspector, manual `/arl unfreeze`/`unlock`/`whitelist` commands, PlaceholderAPI expansion, and Discord Webhooks.

## Version 26.5

- Fixed duplicate `RemovalAction` enum, Folia thread safety, ClearLagg legacy aliases, log rotation, and added `/arl redstone` and `/arl alerts`.
