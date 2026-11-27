# Installation

AntiRedstoneLag runs on Paper, Purpur, and Folia for Minecraft 1.21.4 through 26.3 on Java 21 or newer.

## Quick install

1. Download `AntiRedstoneLag-26.5.jar` from [Modrinth](https://modrinth.com/plugin/antiredstonelag) or [GitHub Releases](https://github.com/synkfr/AntiRedstoneLag/releases).
2. Place the jar inside your server's `plugins/` folder.
3. Restart the server to generate `plugins/AntiRedstoneLag/config.yml` and `plugins/AntiRedstoneLag/messages.yml`.

## Recommended first checks

By default, `removal-action` is set to `FREEZE`. Offending clocks pause for 15 seconds and never break player blocks.

If you run a technical SMP where players build large sorting halls or piston farms and you only want staff notifications:

```text
/arl redstone notify
```

If you need to turn redstone limiting off temporarily without restarting:

```text
/arl redstone off
```

## Upgrading from 26.3 or older

When starting `26.5` against an older `config.yml`, the plugin copies your existing file to `config.yml.bk` before writing new default keys such as `redstone-enabled` and `alerts.notify-on-tps-lag`.

Check your `clearlagg.remove-entities` list if you modified it on an older version. The `26.5` parser maps legacy names such as `Minecart_Tnt` and `Boat` automatically, and new defaults use `Chest_Minecart`, `Hopper_Minecart`, `Tnt_Minecart`, and `Furnace_Minecart`.
