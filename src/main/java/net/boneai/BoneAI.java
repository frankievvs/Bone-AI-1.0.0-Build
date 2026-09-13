package net.boneai;

import net.boneai.command.BoneAICommand;
import net.boneai.command.BoneCommand;
import net.boneai.listener.ChatListener;
import net.boneai.service.AnthropicService;
import org.bukkit.plugin.java.JavaPlugin;

public class BoneAI extends JavaPlugin {

    private AnthropicService aiService;

    @Override
    public void onEnable() {
        // Load config.yml
        saveDefaultConfig();

        // Gemini configuration
        String apiKey = getConfig().getString("gemini.api-key", "");
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

        // Create Gemini AI service
        aiService = new AnthropicService(
                apiKey,
                model,
                maxOutputTokens,
                systemPrompt
        );

        // Register /bone
        if (getCommand("bone") != null) {
            getCommand("bone").setExecutor(
                    new BoneCommand(this)
            );
        }

        // Register /boneai
        if (getCommand("boneai") != null) {
            getCommand("boneai").setExecutor(
                    new BoneAICommand(this)
            );
        }

        // Register chat listener
        getServer().getPluginManager().registerEvents(
                new ChatListener(this),
                this
        );

        getLogger().info("=================================");
        getLogger().info("       BoneAI Enabled!");
        getLogger().info("       Provider: Google Gemini");
        getLogger().info("       Model: " + model);
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("BoneAI disabled.");
    }

    /**
     * Gets the Gemini AI service.
     */
    public AnthropicService getAIService() {
        return aiService;
    }
}
