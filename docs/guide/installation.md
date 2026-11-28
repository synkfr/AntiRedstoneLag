# Installation and Upgrade

AntiRedstoneLag v2.0.0 runs on Paper, Purpur, and Folia for Minecraft 1.21.6+ on Java 21 or newer.

## Quick install

1. Download `AntiRedstoneLag-2.0.0.jar` from [Modrinth](https://modrinth.com/plugin/antiredstonelag) or [GitHub Releases](https://github.com/synkfr/AntiRedstoneLag/releases).
2. Place the jar inside your server's `plugins/` folder.
3. Restart the server to generate `plugins/AntiRedstoneLag/config.yml` and `plugins/AntiRedstoneLag/messages.yml`.

## Upgrading from v1 (`26.5` or older)

When starting v2.0.0 on a server with v1 configuration files:

1. `config.yml` is copied to `config.yml.bk`, and all compatible v1 thresholds, ClearLagg rules, worlds, whitelist chunks, and redstone components are migrated into the new `config-version: 2` `config.yml`.
2. `messages.yml` is copied to `messages.yml.bk` and regenerated with `messages-version: 2` using the clean MiniMessage design palette.
3. Legacy `stats.yml` counters are migrated into the new SQLite/JSON storage backend.

Once upgraded, subsequent restarts and `/arl reload` commands preserve your v2 files without overwriting `config.yml.bk` or `messages.yml.bk`.
