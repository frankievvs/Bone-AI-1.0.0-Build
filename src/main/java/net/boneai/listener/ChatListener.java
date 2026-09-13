package net.boneai.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.boneai.BoneAI;
import net.boneai.service.ChatMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.UUID;

public class ChatListener implements Listener {

    private final BoneAI plugin;

    public ChatListener(BoneAI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {

        String triggerWord = plugin.getConfig()
                .getString("chat.trigger-word", "bone")
                .toLowerCase();

        String plainMessage = PlainTextComponentSerializer
                .plainText()
                .serialize(event.message())
                .trim();

        String lower = plainMessage.toLowerCase();

        if (!lower.startsWith(triggerWord)) {
            return;
        }

        if (plainMessage.length() > triggerWord.length()
                && Character.isLetterOrDigit(
                plainMessage.charAt(triggerWord.length()))) {
            return;
        }

        String remainder = plainMessage
                .substring(triggerWord.length())
                .trim();

        if (remainder.startsWith(",")) {
            remainder = remainder.substring(1).trim();
        }

        if (remainder.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("boneai.use")) {
            return;
        }

        if (plugin.getConfig()
                .getBoolean("chat.hide-trigger-messages", false)) {
            event.setCancelled(true);
        }

        handleQuestion(player, remainder);
    }

    public void handleQuestion(Player player, String question) {

        UUID id = player.getUniqueId();

        int cooldownSeconds = plugin.getConfig()
                .getInt("chat.cooldown-seconds", 5);

        if (plugin.getConversationManager().isOnCooldown(id)) {

            long remaining = plugin.getConversationManager()
                    .secondsRemaining(id);

            player.sendMessage(
                    ChatColor.GRAY + "[BoneAI] "
                            + ChatColor.RED
                            + "Slow down! Try again in "
                            + remaining
                            + "s."
            );

            return;
        }

        plugin.getConversationManager()
                .applyCooldown(id, cooldownSeconds);

        List<ChatMessage> history =
                plugin.getConversationManager()
                        .getHistory(id);

        plugin.getAnthropicService()
                .ask(history, question)
                .thenAccept(answer ->
                        plugin.getServer()
                                .getScheduler()
                                .runTask(plugin, () -> {

                                    plugin.getConversationManager()
                                            .recordExchange(
                                                    id,
                                                    question,
                                                    answer
                                            );

                                    sendWrapped(answer);
                                })
                );
    }

    private void sendWrapped(String message) {

        if (message == null || message.isBlank()) {
            return;
        }

        // Remove AI-generated line breaks
        message = message
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        int lineLength = 60;

        StringBuilder line = new StringBuilder();

        for (String word : message.split(" ")) {

            if (line.length() + word.length() + 1 > lineLength
                    && line.length() > 0) {

                Bukkit.broadcastMessage(
                        ChatColor.WHITE + "["
                                + ChatColor.RED + "BoneAI"
                                + ChatColor.WHITE + "] "
                                + ChatColor.RED
                                + line.toString().trim()
                );

                line = new StringBuilder();
            }

            line.append(word).append(' ');
        }

        if (line.length() > 0) {

            Bukkit.broadcastMessage(
                    ChatColor.WHITE + "["
                            + ChatColor.RED + "BoneAI"
                            + ChatColor.WHITE + "] "
                            + ChatColor.RED
                            + line.toString().trim()
            );
        }
    }
}
