package org.ayosynk.antiRedstoneLag.command;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.manager.SnapshotManager;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class TabCompleteHandler {

    private final AntiRedstoneLag plugin;

    public TabCompleteHandler() {
        this.plugin = null;
    }

    public TabCompleteHandler(AntiRedstoneLag plugin) {
        this.plugin = plugin;
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args == null || args.length == 0 || args.length == 1) {
            String partial = (args != null && args.length > 0) ? args[0].toLowerCase() : "";
            if ("reload".startsWith(partial) && (sender.isOp() || sender.hasPermission("antiredstonelag.reload"))) {
                completions.add("reload");
            }
            if ("stats".startsWith(partial) && (sender.isOp() || sender.hasPermission("antiredstonelag.stats"))) {
                completions.add("stats");
            }
            if ("logs".startsWith(partial) && (sender.isOp() || sender.hasPermission("antiredstonelag.logs"))) {
                completions.add("logs");
            }
            if ("hotspots".startsWith(partial) && (sender.isOp() || sender.hasPermission("antiredstonelag.hotspots"))) {
                completions.add("hotspots");
            }
            if ("inspect".startsWith(partial) && (sender.isOp() || sender.hasPermission("antiredstonelag.inspect"))) {
                completions.add("inspect");
            }
            if ("snapshot".startsWith(partial) && (sender.isOp() || sender.hasPermission("antiredstonelag.snapshot"))) {
                completions.add("snapshot");
            }
            if ("clear".startsWith(partial) && (sender.isOp() || sender.hasPermission("antiredstonelag.clear"))) {
                completions.add("clear");
            }
            if ("help".startsWith(partial)) {
                completions.add("help");
            }

        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (sub.equals("logs") && (sender.isOp() || sender.hasPermission("antiredstonelag.logs"))) {
                if ("download".startsWith(partial)) {
                    completions.add("download");
                }
            } else if (sub.equals("hotspots") && (sender.isOp() || sender.hasPermission("antiredstonelag.hotspots"))) {
                if ("export".startsWith(partial)) {
                    completions.add("export");
                }
                for (int p = 1; p <= 5; p++) {
                    if (String.valueOf(p).startsWith(partial)) {
                        completions.add(String.valueOf(p));
                    }
                }
            } else if (sub.equals("inspect") && (sender.isOp() || sender.hasPermission("antiredstonelag.inspect"))) {
                for (String s : List.of("15", "30", "60", "120")) {
                    if (s.startsWith(partial)) {
                        completions.add(s);
                    }
                }
            } else if (sub.equals("snapshot") && (sender.isOp() || sender.hasPermission("antiredstonelag.snapshot"))) {
                for (String opt : List.of("list", "view", "tp", "clear")) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
                if (plugin != null && plugin.getSnapshotManager() != null) {
                    for (SnapshotManager.Snapshot snap : plugin.getSnapshotManager().getSnapshots()) {
                        if (snap.id.toLowerCase().startsWith(partial)) {
                            completions.add(snap.id);
                        }
                    }
                }
            } else if ((sub.equals("clear") || sub.equals("clearlagg")) && (sender.isOp() || sender.hasPermission("antiredstonelag.clear"))) {
                for (String opt : List.of("items", "all", "mobs", "projectiles", "xp", "vehicles", "count", "cancel", "timer")) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String action = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            if (sub.equals("snapshot") && (action.equals("view") || action.equals("tp")) && (sender.isOp() || sender.hasPermission("antiredstonelag.snapshot"))) {
                if (plugin != null && plugin.getSnapshotManager() != null) {
                    for (SnapshotManager.Snapshot snap : plugin.getSnapshotManager().getSnapshots()) {
                        if (snap.id.toLowerCase().startsWith(partial)) {
                            completions.add(snap.id);
                        }
                    }
                }
            }
        }

        return completions;
    }
}