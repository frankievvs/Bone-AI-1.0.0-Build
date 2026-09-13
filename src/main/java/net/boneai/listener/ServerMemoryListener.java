package net.boneai.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.boneai.BoneAI;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Watches everything happening on the server - not just messages aimed at
 * BoneAI - and records it into ServerMemory so BoneAI can build up
 * knowledge of the server over time and comment on things spontaneously.
 */
public class ServerMemoryListener implements Listener {

    private final BoneAI plugin;

    public ServerMemoryListener(BoneAI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (plainMessage.isEmpty()) {
            return;
        }
        plugin.getServerMemory().recordChat(event.getPlayer().getName(), plainMessage);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServerMemory().recordEvent(event.getPlayer().getName() + " joined the server.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getServerMemory().recordEvent(event.getPlayer().getName() + " left the server.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        String deathMessage = event.deathMessage() != null
                ? PlainTextComponentSerializer.plainText().serialize(event.deathMessage())
                : event.getEntity().getName() + " died.";
        plugin.getServerMemory().recordEvent(deathMessage);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().getKey();
        // Skip Minecraft's internal "recipe unlocked" noise - only record real advancements.
        if (key.startsWith("recipes/")) {
            return;
        }
        plugin.getServerMemory().recordEvent(
                event.getPlayer().getName() + " completed the advancement: " + key);
    }
}
