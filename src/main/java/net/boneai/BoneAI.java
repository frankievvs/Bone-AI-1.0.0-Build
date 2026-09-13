package net.boneai;

import net.boneai.command.BoneAICommand;
import net.boneai.command.BoneCommand;
import net.boneai.listener.ChatListener;
import net.boneai.service.AnthropicService;
import net.boneai.service.ConversationManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BoneAI extends JavaPlugin {

    private AnthropicService anthropicService;
    private ConversationManager conversationManager;
    private ChatListener chatListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        initializeServices();

        // Create and register chat listener
        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

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

        getLogger().info("=================================");
        getLogger().info("          BoneAI Enabled!");
        getLogger().info("          Provider: Google Gemini");
        getLogger().info("=================================");
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

    @Override
    public void onDisable() {
        getLogger().info("BoneAI disabled.");
    }
}
