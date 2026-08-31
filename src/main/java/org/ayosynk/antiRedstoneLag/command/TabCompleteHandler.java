package org.ayosynk.antiRedstoneLag.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class TabCompleteHandler {

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("reload".startsWith(partial) && sender.hasPermission("antiredstonelag.reload")) {
                completions.add("reload");
            }
            if ("stats".startsWith(partial) && sender.hasPermission("antiredstonelag.stats")) {
                completions.add("stats");
            }
            if ("logs".startsWith(partial) && sender.hasPermission("antiredstonelag.logs")) {
                completions.add("logs");
            }
            if ("hotspots".startsWith(partial) && sender.hasPermission("antiredstonelag.hotspots")) {
                completions.add("hotspots");
            }
            if ("inspect".startsWith(partial) && sender.hasPermission("antiredstonelag.inspect")) {
                completions.add("inspect");
            }
            if ("help".startsWith(partial)) {
                completions.add("help");
            }

        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (sub.equals("logs") && sender.hasPermission("antiredstonelag.logs")) {
                if ("download".startsWith(partial)) {
                    completions.add("download");
                }
            } else if (sub.equals("hotspots") && sender.hasPermission("antiredstonelag.hotspots")) {
                if ("export".startsWith(partial)) {
                    completions.add("export");
                }
                for (int p = 1; p <= 5; p++) {
                    if (String.valueOf(p).startsWith(partial)) {
                        completions.add(String.valueOf(p));
                    }
                }
            } else if (sub.equals("inspect") && sender.hasPermission("antiredstonelag.inspect")) {
                for (String s : List.of("15", "30", "60", "120")) {
                    if (s.startsWith(partial)) {
                        completions.add(s);
                    }
                }
            }
        }

        return completions;
    }
}