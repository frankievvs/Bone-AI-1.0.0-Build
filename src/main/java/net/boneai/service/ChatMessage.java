package net.boneai.service;

/**
 * A single turn in a conversation, matching the Anthropic Messages API shape:
 * role is "user" or "assistant".
 */
public record ChatMessage(String role, String content) {
}
