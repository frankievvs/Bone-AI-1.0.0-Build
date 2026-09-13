package net.boneai.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Tracks what's happening on the server so BoneAI can build up "knowledge"
 * of it over time.
 * <p>
 * Two layers:
 * - A short-term raw log (chat lines, joins/leaves, deaths, advancements)
 *   kept in memory only - lost on restart, capped to a max line count.
 * - A longer-term list of AI-written knowledge summaries, persisted to
 *   memory.json, pruned after a configurable retention period.
 */
public class ServerMemory {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());

    public record MemoryEntry(long timestamp, String text) {
    }

    public record KnowledgeEntry(long timestamp, String summary) {
    }

    private final ConcurrentLinkedDeque<MemoryEntry> rawLog = new ConcurrentLinkedDeque<>();
    private final List<KnowledgeEntry> knowledge = new CopyOnWriteArrayList<>();

    private final File storageFile;
    private final Gson gson = new GsonBuilder().create();
    private final Logger logger;

    private final int maxRawLogLines;
    private final int knowledgeRetentionDays;

    // Entries with a timestamp at or before this have already been folded
    // into a knowledge summary (or judged not worth remembering).
    private volatile long lastSummarizedTimestamp = 0L;

    public ServerMemory(File dataFolder, int maxRawLogLines, int knowledgeRetentionDays, Logger logger) {
        this.storageFile = new File(dataFolder, "memory.json");
        this.maxRawLogLines = maxRawLogLines;
        this.knowledgeRetentionDays = knowledgeRetentionDays;
        this.logger = logger;
        load();
    }

    public void recordChat(String playerName, String message) {
        record(playerName + ": " + message);
    }

    public void recordEvent(String description) {
        record(description);
    }

    private void record(String text) {
        rawLog.addLast(new MemoryEntry(System.currentTimeMillis(), text));
        while (rawLog.size() > maxRawLogLines) {
            rawLog.pollFirst();
        }
    }

    /** Raw log entries that haven't been folded into a knowledge summary yet. */
    public List<MemoryEntry> getUnsummarizedEntries() {
        List<MemoryEntry> result = new ArrayList<>();
        for (MemoryEntry entry : rawLog) {
            if (entry.timestamp() > lastSummarizedTimestamp) {
                result.add(entry);
            }
        }
        return result;
    }

    public String formatEntries(List<MemoryEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (MemoryEntry entry : entries) {
            sb.append('[').append(TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()))).append("] ")
                    .append(entry.text()).append('\n');
        }
        return sb.toString();
    }

    /** The most recent raw log lines, formatted - used for spontaneous-comment context. */
    public String getRecentLogText(int maxLines) {
        List<MemoryEntry> snapshot = new ArrayList<>(rawLog);
        int from = Math.max(0, snapshot.size() - maxLines);
        return formatEntries(snapshot.subList(from, snapshot.size()));
    }

    public boolean hasAnyActivity() {
        return !rawLog.isEmpty();
    }

    /**
     * Records the outcome of a summarization attempt. Pass the AI's summary
     * (or null/blank/"NONE" if there was nothing worth remembering) plus the
     * timestamp of the newest entry that was considered, so we don't
     * re-summarize the same mundane chat over and over.
     */
    public void addKnowledge(String summary, long upThroughTimestamp) {
        if (summary != null && !summary.isBlank() && !summary.trim().equalsIgnoreCase("NONE")) {
            knowledge.add(new KnowledgeEntry(System.currentTimeMillis(), summary.trim()));
            pruneKnowledge();
            save();
        }
        lastSummarizedTimestamp = Math.max(lastSummarizedTimestamp, upThroughTimestamp);
    }

    private void pruneKnowledge() {
        long cutoff = System.currentTimeMillis() - (knowledgeRetentionDays * 24L * 60 * 60 * 1000);
        knowledge.removeIf(entry -> entry.timestamp() < cutoff);
    }

    /** All retained knowledge, formatted for use as AI context. Empty string if none. */
    public String getKnowledgeContext() {
        pruneKnowledge();
        if (knowledge.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeEntry entry : knowledge) {
            sb.append('[').append(TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()))).append("] ")
                    .append(entry.summary()).append('\n');
        }
        return sb.toString();
    }

    private void load() {
        if (!storageFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(storageFile)) {
            Type type = new TypeToken<List<KnowledgeEntry>>() {
            }.getType();
            List<KnowledgeEntry> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                knowledge.addAll(loaded);
                pruneKnowledge();
                for (KnowledgeEntry entry : knowledge) {
                    lastSummarizedTimestamp = Math.max(lastSummarizedTimestamp, entry.timestamp());
                }
            }
        } catch (IOException e) {
            logger.warning("Could not load BoneAI memory.json: " + e.getMessage());
        }
    }

    public void save() {
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(storageFile)) {
                gson.toJson(knowledge, writer);
            }
        } catch (IOException e) {
            logger.warning("Could not save BoneAI memory.json: " + e.getMessage());
        }
    }
}
