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
        loadServices();

        this.chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        BoneCommand boneCommand = new BoneCommand(this);
        getCommand("bone").setExecutor(boneCommand);
        getCommand("bone").setTabCompleter(boneCommand);

        getCommand("boneai").setExecutor(new BoneAICommand(this));

        String triggerWord = getConfig().getString("chat.trigger-word", "bone");
        getLogger().info("BoneAI is online. Players can use /bone <message> or type \""
                + triggerWord + ", <message>\" in chat.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BoneAI is shutting down.");
    }

    /**
     * (Re)builds the services from the current in-memory config. Called on
     * enable and whenever /boneai reload runs.
     */
    public void loadServices() {
        String apiKey = getConfig().getString("api.key", "");
        String model = getConfig().getString("api.model", "claude-haiku-4-5-20251001");
        int maxTokens = getConfig().getInt("api.max-tokens", 300);
        String systemPrompt = getConfig().getString("api.system-prompt", "");

        this.anthropicService = new AnthropicService(apiKey, model, maxTokens, systemPrompt);

        boolean memoryEnabled = getConfig().getBoolean("chat.memory.enabled", true);
        int maxHistory = getConfig().getInt("chat.memory.max-messages", 6);
        this.conversationManager = new ConversationManager(memoryEnabled, maxHistory);

        if (apiKey == null || apiKey.isBlank() || apiKey.equals("YOUR_ANTHROPIC_API_KEY")) {
            getLogger().warning("No Anthropic API key set in config.yml - BoneAI will reply with a "
                    + "setup message until one is added.");
        }
    }

    public void reload() {
        reloadConfig();
        loadServices();
    }

    public AnthropicService getAnthropicService() {
        return anthropicService;
    }

    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }
}
