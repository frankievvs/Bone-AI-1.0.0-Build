package net.boneai.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks rolling per-player conversation history and request cooldowns.
 * All maps are per-player (keyed by UUID) so this scales fine across an SMP.
 */
public class ConversationManager {

    private final Map<UUID, Deque<ChatMessage>> histories = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownExpiry = new ConcurrentHashMap<>();

    private final boolean memoryEnabled;
    private final int maxHistoryMessages;

    public ConversationManager(boolean memoryEnabled, int maxHistoryMessages) {
        this.memoryEnabled = memoryEnabled;
        this.maxHistoryMessages = Math.max(0, maxHistoryMessages);
    }

    public List<ChatMessage> getHistory(UUID playerId) {
        if (!memoryEnabled) {
            return List.of();
        }
        Deque<ChatMessage> deque = histories.get(playerId);
        return deque == null ? List.of() : new ArrayList<>(deque);
    }

    public void recordExchange(UUID playerId, String userMessage, String assistantMessage) {
        if (!memoryEnabled || maxHistoryMessages <= 0) {
            return;
        }
        Deque<ChatMessage> deque = histories.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        deque.addLast(new ChatMessage("user", userMessage));
        deque.addLast(new ChatMessage("assistant", assistantMessage));
        while (deque.size() > maxHistoryMessages) {
            deque.pollFirst();
        }
    }

    public void clearHistory(UUID playerId) {
        histories.remove(playerId);
    }

    public boolean isOnCooldown(UUID playerId) {
        Long expiry = cooldownExpiry.get(playerId);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public long secondsRemaining(UUID playerId) {
        Long expiry = cooldownExpiry.get(playerId);
        if (expiry == null) {
            return 0;
        }
        long remainingMs = expiry - System.currentTimeMillis();
        return Math.max(0, (remainingMs + 999) / 1000);
    }

    public void applyCooldown(UUID playerId, int cooldownSeconds) {
        cooldownExpiry.put(playerId, System.currentTimeMillis() + (cooldownSeconds * 1000L));
    }
}
