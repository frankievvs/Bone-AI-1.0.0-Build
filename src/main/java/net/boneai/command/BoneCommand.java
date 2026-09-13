package net.boneai.command;

import net.boneai.BoneAI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class BoneCommand implements CommandExecutor, TabCompleter {

    private final BoneAI plugin;

    public BoneCommand(BoneAI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can talk to BoneAI in-game.");
            return true;
        }

        if (!player.hasPermission("boneai.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use BoneAI.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /bone <message>");
            return true;
        }

        String question = String.join(" ", args);
        plugin.getChatListener().handleQuestion(player, question);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
