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

public class AnthropicService {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxOutputTokens;
    private final String systemPrompt;

    public AnthropicService(
            String apiKey,
            String model,
            int maxOutputTokens,
            String systemPrompt
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.systemPrompt = systemPrompt;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<String> ask(
            List<ChatMessage> history,
            String newUserMessage
    ) {
        return ask(history, newUserMessage, null);
    }

    /**
     * Same as ask(), but with extra context (e.g. accumulated server
     * knowledge) appended to BoneAI's system prompt for this call only.
     */
    public CompletableFuture<String> ask(
            List<ChatMessage> history,
            String newUserMessage,
            String extraContext
    ) {

        if (apiKey.isBlank()
                || apiKey.equals("YOUR_GEMINI_API_KEY")
                || apiKey.equals("PUT_YOUR_NEW_GEMINI_API_KEY_HERE")) {

            return CompletableFuture.completedFuture(
                    "BoneAI isn't configured yet - ask an admin to set a Gemini API key in config.yml."
            );
        }

        JsonObject body = new JsonObject();

        JsonArray contents = new JsonArray();

        for (ChatMessage msg : history) {
            contents.add(
                    toContentObject(
                            msg.role(),
                            msg.content()
                    )
            );
        }

        contents.add(
                toContentObject(
                        "user",
                        newUserMessage
                )
        );

        body.add("contents", contents);

        String combinedSystemPrompt = systemPrompt == null ? "" : systemPrompt;
        if (extraContext != null && !extraContext.isBlank()) {
            combinedSystemPrompt = combinedSystemPrompt
                    + "\n\nWhat you know about this server so far (use it when relevant, "
                    + "don't force it into every answer):\n" + extraContext;
        }

        if (!combinedSystemPrompt.isBlank()) {

            JsonObject systemInstruction = new JsonObject();

            JsonArray parts = new JsonArray();

            JsonObject part = new JsonObject();
            part.addProperty("text", combinedSystemPrompt);

            parts.add(part);

            systemInstruction.add("parts", parts);

            body.add(
                    "systemInstruction",
                    systemInstruction
            );
        }

        JsonObject generationConfig = new JsonObject();

        generationConfig.addProperty(
                "maxOutputTokens",
                maxOutputTokens
        );

        body.add(
                "generationConfig",
                generationConfig
        );

        return sendBody(body);
    }

    /**
     * A one-off call that doesn't use BoneAI's normal personality/system
     * prompt or conversation history - used for internal tasks like
     * summarizing the chat log or deciding whether to comment spontaneously.
     */
    public CompletableFuture<String> askRaw(
            String systemPromptOverride,
            int maxOutputTokensOverride,
            String userPrompt
    ) {
        if (apiKey.isBlank()
                || apiKey.equals("YOUR_GEMINI_API_KEY")
                || apiKey.equals("PUT_YOUR_NEW_GEMINI_API_KEY_HERE")) {
            return CompletableFuture.completedFuture("");
        }

        JsonObject body = new JsonObject();

        JsonArray contents = new JsonArray();
        contents.add(toContentObject("user", userPrompt));
        body.add("contents", contents);

        if (systemPromptOverride != null && !systemPromptOverride.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", systemPromptOverride);
            parts.add(part);
            systemInstruction.add("parts", parts);
            body.add("systemInstruction", systemInstruction);
        }

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", maxOutputTokensOverride);
        body.add("generationConfig", generationConfig);

        return sendBody(body);
    }

    private CompletableFuture<String> sendBody(JsonObject body) {
        String endpoint = String.format(
                ENDPOINT_TEMPLATE,
                model
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Content-Type",
                        "application/json"
                )
                .header(
                        "x-goog-api-key",
                        apiKey
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                body.toString()
                        )
                )
                .build();

        return httpClient
                .sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                )
                .thenApply(this::parseResponse)
                .exceptionally(ex ->
                        "BoneAI couldn't reach the AI service: "
                                + ex.getMessage()
                );
    }

    private JsonObject toContentObject(
            String role,
            String text
    ) {

        JsonObject content = new JsonObject();

        /*
         * Gemini uses "model" for assistant responses.
         * User messages remain "user".
         */
        String geminiRole = role.equalsIgnoreCase("assistant")
                ? "model"
                : role;

        content.addProperty(
                "role",
                geminiRole
        );

        JsonArray parts = new JsonArray();

        JsonObject part = new JsonObject();

        part.addProperty(
                "text",
                text
        );

        parts.add(part);

        content.add(
                "parts",
                parts
        );

        return content;
    }

    private String parseResponse(
            HttpResponse<String> response
    ) {

        int statusCode = response.statusCode();
        String responseBody = response.body();

        try {

            JsonObject json =
                    JsonParser
                            .parseString(responseBody)
                            .getAsJsonObject();

            /*
             * SUCCESS
             */
            if (statusCode >= 200 && statusCode < 300) {

                if (!json.has("candidates")
                        || json.getAsJsonArray("candidates").isEmpty()) {

                    return "BoneAI got an empty response. Try again in a moment.";
                }

                JsonObject firstCandidate =
                        json.getAsJsonArray("candidates")
                                .get(0)
                                .getAsJsonObject();

                if (!firstCandidate.has("content")) {

                    return "BoneAI didn't have anything to say. Try rephrasing.";
                }

                JsonObject content =
                        firstCandidate
                                .getAsJsonObject("content");

                if (!content.has("parts")) {

                    return "BoneAI received an empty response.";
                }

                JsonArray parts =
                        content.getAsJsonArray("parts");

                StringBuilder result =
                        new StringBuilder();

                for (int i = 0; i < parts.size(); i++) {

                    JsonObject part =
                            parts.get(i)
                                    .getAsJsonObject();

                    if (part.has("text")) {

                        result.append(
                                part.get("text")
                                        .getAsString()
                        );
                    }
                }

                String text =
                        result.toString().trim();

                return text.isEmpty()
                        ? "BoneAI didn't have anything to say. Try rephrasing."
                        : text;
            }

            /*
             * GOOGLE API ERROR
             */

            if (json.has("error")) {

                JsonObject error =
                        json.getAsJsonObject("error");

                String message =
                        error.has("message")
                                ? error.get("message").getAsString()
                                : "Unknown Google API error";

                String status =
                        error.has("status")
                                ? error.get("status").getAsString()
                                : "UNKNOWN";

                return "Gemini API error "
                        + statusCode
                        + " ("
                        + status
                        + "): "
                        + message;
            }

            return "Gemini API error "
                    + statusCode
                    + ": "
                    + responseBody;

        } catch (Exception e) {

            return "Gemini API error "
                    + statusCode
                    + ": "
                    + responseBody;
        }
    }
}
