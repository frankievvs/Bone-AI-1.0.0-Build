package net.boneai;

import net.boneai.command.BoneAICommand;
import net.boneai.command.BoneCommand;
import net.boneai.listener.ChatListener;
import net.boneai.listener.ServerMemoryListener;
import net.boneai.service.AnthropicService;
import net.boneai.service.ConversationManager;
import net.boneai.service.ServerMemory;
import net.boneai.task.MemorySummarizerTask;
import net.boneai.task.SpontaneousChatterTask;
import org.bukkit.plugin.java.JavaPlugin;

public class BoneAI extends JavaPlugin {

    private AnthropicService anthropicService;
    private ConversationManager conversationManager;
    private ChatListener chatListener;
    private ServerMemory serverMemory;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        initializeServices();
        initializeServerMemory();

        // Create and register chat listener
        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        // Passively watches all chat/events for the server-memory system
        getServer().getPluginManager().registerEvents(new ServerMemoryListener(this), this);

        // Register /bone
        if (getCommand("bone") != null) {
            getCommand("bone").setExecutor(new BoneCommand(this));
        } else {
            getLogger().warning("Command 'bone' is not defined in plugin.yml!");
        }

        // Register /boneai
        if (getCommand("boneai") != null) {
            getCommand("boneai").setExecutor(new BoneAICommand(this));
        } else {
            getLogger().warning("Command 'boneai' is not defined in plugin.yml!");
        }

        scheduleBackgroundTasks();

        getLogger().info("=================================");
        getLogger().info("          BoneAI Enabled!");
        getLogger().info("          Provider: Google Gemini");
        getLogger().info("=================================");
    }

    /**
     * Sets up the server-memory system (raw chat/event log + persisted
     * knowledge summaries). Only created once - it isn't rebuilt on
     * /boneai reload, so accumulated memory survives config reloads.
     */
    private void initializeServerMemory() {
        if (serverMemory != null) {
            return;
        }
        int maxRawLogLines = getConfig().getInt("server-memory.max-raw-log-lines", 400);
        int retentionDays = getConfig().getInt("server-memory.knowledge-retention-days", 3);
        serverMemory = new ServerMemory(getDataFolder(), maxRawLogLines, retentionDays, getLogger());
    }

    /**
     * Schedules the two background timers: one that periodically compresses
     * recent activity into long-term knowledge, and one that occasionally
     * lets BoneAI comment on its own without the trigger word.
     */
    private void scheduleBackgroundTasks() {
        if (getConfig().getBoolean("server-memory.enabled", true)) {
            long summarizeIntervalTicks = getConfig().getLong("server-memory.summarize-interval-minutes", 20) * 60L * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(
                    this, new MemorySummarizerTask(this), summarizeIntervalTicks, summarizeIntervalTicks);
        }

        if (getConfig().getBoolean("spontaneous.enabled", true)) {
            long spontaneousIntervalTicks = getConfig().getLong("spontaneous.interval-minutes", 5) * 60L * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(
                    this, new SpontaneousChatterTask(this), spontaneousIntervalTicks, spontaneousIntervalTicks);
        }
    }

    /**
     * Initializes the Gemini service and conversation manager.
     */
    private void initializeServices() {

        // -----------------------------
        // Gemini configuration
        // -----------------------------

        String apiKey = getConfig().getString(
                "gemini.api-key",
                ""
        );

        String model = getConfig().getString(
                "gemini.model",
                "gemini-2.5-flash-lite"
        );

        int maxOutputTokens = getConfig().getInt(
                "gemini.max-output-tokens",
                500
        );

        String systemPrompt = getConfig().getString(
                "gemini.system-prompt",
                "You are BoneAI, an AI assistant on a Minecraft server. " +
                "Be helpful, friendly, and concise."
        );

        anthropicService = new AnthropicService(
                apiKey,
                model,
                maxOutputTokens,
                systemPrompt
        );

        // -----------------------------
        // Conversation configuration
        // -----------------------------

        boolean memoryEnabled = getConfig().getBoolean(
                "memory.enabled",
                true
        );

        int maxHistoryMessages = getConfig().getInt(
                "memory.max-history-messages",
                20
        );

        conversationManager = new ConversationManager(
                memoryEnabled,
                maxHistoryMessages
        );

        getLogger().info("Gemini model: " + model);
        getLogger().info("Conversation memory: " +
                (memoryEnabled ? "enabled" : "disabled"));
    }

    /**
     * Reloads BoneAI's configuration and services.
     *
     * This is used by /boneai reload.
     */
    public void reload() {
        reloadConfig();
        initializeServices();
    }

    /**
     * Returns the Gemini/AI service.
     *
     * The method keeps the old AnthropicService name so
     * existing commands/listeners continue working.
     */
    public AnthropicService getAnthropicService() {
        return anthropicService;
    }

    /**
     * Returns the conversation manager.
     */
    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    /**
     * Returns the chat listener.
     */
    public ChatListener getChatListener() {
        return chatListener;
    }

    /**
     * Alias for the AI service.
     * Useful if you want to migrate the naming from
     * AnthropicService to GeminiService later.
     */
    public AnthropicService getAIService() {
        return anthropicService;
    }

    /**
     * Returns the server-memory service (raw log + persisted knowledge).
     */
    public ServerMemory getServerMemory() {
        return serverMemory;
    }

    @Override
    public void onDisable() {
        if (serverMemory != null) {
            serverMemory.save();
        }
        getLogger().info("BoneAI disabled.");
    }
}
