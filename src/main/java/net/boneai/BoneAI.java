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
 * Talks to Google's Gemini API (generateContent). The class name is kept as
 * AnthropicService so nothing else in the plugin needs to change - despite
 * the name, this calls Gemini, chosen here because it has a genuine free
 * tier (no credit card, Flash-Lite model has generous daily quotas).
 */
public class AnthropicService {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxOutputTokens;
    private final String systemPrompt;

    public AnthropicService(String apiKey, String model, int maxOutputTokens, String systemPrompt) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.systemPrompt = systemPrompt;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<String> ask(List<ChatMessage> history, String newUserMessage) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("YOUR_GEMINI_API_KEY")) {
            return CompletableFuture.completedFuture(
                    "BoneAI isn't configured yet - ask an admin to set a Gemini API key in config.yml.");
        }

        JsonObject body = new JsonObject();

        JsonArray contents = new JsonArray();
        for (ChatMessage msg : history) {
            contents.add(toContentObject(msg.role(), msg.content()));
        }
        contents.add(toContentObject("user", newUserMessage));
        body.add("contents", contents);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", systemPrompt);
            parts.add(part);
            systemInstruction.add("parts", parts);
            body.add("systemInstruction", systemInstruction);
        }

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", maxOutputTokens);
        body.add("generationConfig", generationConfig);

        String endpoint = String.format(ENDPOINT_TEMPLATE, model);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse)
                .exceptionally(ex -> "BoneAI couldn't reach the AI service right now ("
                        + ex.getMessage() + ").");
    }

    private JsonObject toContentObject(String role, String text) {
        JsonObject content = new JsonObject();
        content.addProperty("role", role);
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        parts.add(part);
        content.add("parts", parts);
        return content;
    }

    private String parseResponse(HttpResponse<String> response) {
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                String message = error.has("message") ? error.get("message").getAsString() : "unknown error";
                String lower = message.toLowerCase();
                if (lower.contains("quota") || lower.contains("resource_exhausted")) {
                    return "BoneAI hit the free tier's rate limit - try again in a bit.";
                }
                return "BoneAI hit an API error: " + message;
            }

            if (!json.has("candidates") || json.getAsJsonArray("candidates").isEmpty()) {
                return "BoneAI got an empty response. Try again in a moment.";
            }

            JsonObject firstCandidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
            if (!firstCandidate.has("content")) {
                return "BoneAI didn't have anything to say. Try rephrasing.";
            }

            JsonObject content = firstCandidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                JsonObject part = parts.get(i).getAsJsonObject();
                if (part.has("text")) {
                    sb.append(part.get("text").getAsString());
                }
            }
            String text = sb.toString().trim();
            return text.isEmpty() ? "BoneAI didn't have anything to say. Try rephrasing." : text;
        } catch (Exception e) {
            return "BoneAI couldn't parse the AI response (" + e.getMessage() + ").";
        }
    }
}
