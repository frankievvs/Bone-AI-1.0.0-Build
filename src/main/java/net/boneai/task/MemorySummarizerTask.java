package net.boneai.task;

import net.boneai.BoneAI;
import net.boneai.service.ServerMemory;

import java.util.List;

/**
 * Runs on a timer. Takes whatever raw chat/events have piled up since the
 * last run and asks the AI to compress it into a handful of short, durable
 * facts worth remembering - or nothing, if it was all mundane chatter. This
 * is how BoneAI "learns" the server over days without needing to replay the
 * entire chat history on every request.
 */
public class MemorySummarizerTask implements Runnable {

    private static final int MAX_OUTPUT_TOKENS = 250;

    private static final String SUMMARY_SYSTEM_PROMPT =
            "You are compressing a Minecraft SMP server's recent chat/event log into short, "
                    + "durable facts worth remembering long-term: notable builds, ongoing drama or "
                    + "running jokes, auctions or trades, rivalries or friendships, deaths, "
                    + "achievements, and anything a server historian would want on record. "
                    + "Ignore mundane back-and-forth chatter with no lasting relevance. "
                    + "Respond with a short bullet list, each fact on its own line starting with '- '. "
                    + "If nothing in the log is worth remembering long-term, respond with exactly: NONE";

    private final BoneAI plugin;

    public MemorySummarizerTask(BoneAI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ServerMemory memory = plugin.getServerMemory();
        List<ServerMemory.MemoryEntry> unsummarized = memory.getUnsummarizedEntries();

        if (unsummarized.isEmpty()) {
            return;
        }

        long upThroughTimestamp = unsummarized.get(unsummarized.size() - 1).timestamp();
        String logText = memory.formatEntries(unsummarized);

        plugin.getAnthropicService()
                .askRaw(SUMMARY_SYSTEM_PROMPT, MAX_OUTPUT_TOKENS, logText)
                .thenAccept(summary -> memory.addKnowledge(summary, upThroughTimestamp));
    }
}
