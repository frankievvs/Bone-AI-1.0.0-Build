package net.boneai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the Anthropic Messages API. All calls are async and never touch
 * the Bukkit main thread directly - callers are responsible for hopping
 * back with the scheduler before touching Bukkit API objects.
 */
public class AnthropicService {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final String systemPrompt;

    public AnthropicService(String apiKey, String model, int maxTokens, String systemPrompt) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.systemPrompt = systemPrompt;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<String> ask(List<ChatMessage> history, String newUserMessage) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("YOUR_ANTHROPIC_API_KEY")) {
            return CompletableFuture.completedFuture(
                    "BoneAI isn't configured yet - ask an admin to set an API key in config.yml.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.addProperty("system", systemPrompt);
        }

        JsonArray messages = new JsonArray();
        for (ChatMessage msg : history) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            messages.add(m);
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", newUserMessage);
        messages.add(userMsg);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse)
                .exceptionally(ex -> "BoneAI couldn't reach the AI service right now ("
                        + ex.getMessage() + ").");
    }

    private String parseResponse(HttpResponse<String> response) {
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                String message = error.has("message") ? error.get("message").getAsString() : "unknown error";
                return "BoneAI hit an API error: " + message;
            }

            if (!json.has("content")) {
                return "BoneAI got an empty response. Try again in a moment.";
            }

            JsonArray content = json.getAsJsonArray("content");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.size(); i++) {
                JsonObject block = content.get(i).getAsJsonObject();
                if (block.has("type") && "text".equals(block.get("type").getAsString())) {
                    sb.append(block.get("text").getAsString());
                }
            }
            String text = sb.toString().trim();
            return text.isEmpty() ? "BoneAI didn't have anything to say. Try rephrasing." : text;
        } catch (Exception e) {
            return "BoneAI couldn't parse the AI response (" + e.getMessage() + ").";
        }
    }
}
