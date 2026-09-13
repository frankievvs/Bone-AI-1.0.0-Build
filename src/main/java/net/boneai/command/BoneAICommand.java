package net.boneai.command;

import net.boneai.BoneAI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BoneAICommand implements CommandExecutor {

    private final BoneAI plugin;

    public BoneAICommand(BoneAI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boneai.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "BoneAI commands: /boneai reload, /boneai clear <player>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "BoneAI config reloaded.");
            }
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /boneai clear <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                    return true;
                }
                plugin.getConversationManager().clearHistory(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Cleared BoneAI history for " + target.getName() + ".");
            }
            default -> sender.sendMessage(ChatColor.YELLOW
                    + "Unknown subcommand. Use /boneai reload or /boneai clear <player>.");
        }
        return true;
    }
}
