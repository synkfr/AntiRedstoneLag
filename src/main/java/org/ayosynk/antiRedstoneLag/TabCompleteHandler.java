package org.ayosynk.antiRedstoneLag;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class TabCompleteHandler implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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
            if ("help".startsWith(partial)) {
                completions.add("help");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("logs")) {
            if ("download".startsWith(args[1].toLowerCase()) && sender.hasPermission("antiredstonelag.logs")) {
                completions.add("download");
            }
        }

        return completions;
    }
}