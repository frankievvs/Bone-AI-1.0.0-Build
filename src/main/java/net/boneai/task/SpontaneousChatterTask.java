package net.boneai.task;

import net.boneai.BoneAI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * Runs on a timer. Each run, it hands BoneAI a snippet of recent chat/events
 * and lets the AI itself decide whether anything is worth a short,
 * spontaneous comment - if not, it stays quiet. This is what gives BoneAI
 * its "randomly chimes in" behavior without needing the trigger word.
 */
public class SpontaneousChatterTask implements Runnable {

    private static final int RECENT_LINES_TO_CONSIDER = 40;
    private static final int MAX_OUTPUT_TOKENS = 150;

    private final BoneAI plugin;

    public SpontaneousChatterTask(BoneAI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getServerMemory().hasAnyActivity()) {
            return;
        }

        String recentLog = plugin.getServerMemory().getRecentLogText(RECENT_LINES_TO_CONSIDER);
        if (recentLog.isBlank()) {
            return;
        }

        String knowledge = plugin.getServerMemory().getKnowledgeContext();

        String systemPrompt = plugin.getConfig().getString("spontaneous.system-prompt", "")
                + "\n\nBackground knowledge about this server so far:\n"
                + (knowledge.isBlank() ? "(nothing recorded yet)" : knowledge);

        plugin.getAnthropicService()
                .askRaw(systemPrompt, MAX_OUTPUT_TOKENS, recentLog)
                .thenAccept(response -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String trimmed = response == null ? "" : response.trim();
                    if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("NONE")) {
                        return;
                    }
                    String lower = trimmed.toLowerCase();
                    if (lower.startsWith("gemini api error") || lower.startsWith("boneai couldn't reach")) {
                        // Don't spam the server with error messages from a background task.
                        return;
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[BoneAI] " + ChatColor.YELLOW + trimmed);
                }));
    }
}
