package com.git.hui.jobclaw.agents.identity.soul;

import com.git.hui.jobclaw.core.agent.llm.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Extracts and updates agent soul profile from conversation history.
 *
 * <p>Features:
 * <ul>
 *   <li>Async soul extraction to avoid blocking</li>
 *   <li>Incremental update based on existing soul</li>
 *   <li>Fallback to existing soul on failure</li>
 *   <li>Prompt template based</li>
 *   <li>Considers user profile for context</li>
 * </ul>
 *
 * AIDEV-NOTE: Soul extractor for Phase 2 implementation
 */
@Component
public class UserAgentSoulExtractor {

    private static final Logger log = LoggerFactory.getLogger(UserAgentSoulExtractor.class);

    private static final int MAX_CONVERSATION_FOR_EXTRACTION = 50;
    private final String promptTemplate;
    private final LlmCaller llmCaller;

    public UserAgentSoulExtractor(
            @Value("classpath:/prompts/agent-soul-extraction-prompt.md") Resource promptResource,
            LlmCaller simpleLlmCaller) {
        this.llmCaller = simpleLlmCaller;
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("SoulExtractor initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load soul extraction prompt template", e);
            throw new RuntimeException("Failed to initialize SoulExtractor", e);
        }
    }

    /**
     * Extract agent soul asynchronously.
     *
     * @param user user ID
     * @param currentSoul current soul profile (may be empty)
     * @param messages conversation messages
     * @param userProfile user profile content (user.md)
     * @return CompletableFuture with updated soul markdown
     */
    public CompletableFuture<String> extractAsync(UserConversationInfo user, String currentSoul,
                                                  List<Message> messages, String userProfile) {
        return CompletableFuture.supplyAsync(() -> extract(user, currentSoul, messages, userProfile));
    }

    /**
     * Extract agent soul synchronously.
     *
     * @param user user ID
     * @param currentSoul current soul profile (may be empty)
     * @param messages conversation messages
     * @param userProfile user profile content (user.md)
     * @return updated soul markdown, or existing soul if failed
     */
    public String extract(UserConversationInfo user, String currentSoul, List<Message> messages, String userProfile) {
        if (messages == null || messages.isEmpty()) {
            log.debug("No messages to extract soul from");
            return currentSoul;
        }

        String jobClawUserId = user.jobClawUserId();
        try {
            log.info("Extracting soul for user: {} ({} messages)", jobClawUserId, messages.size());

            // Prepare conversation text
            String conversationText = formatConversation(messages);

            // Use existing soul or empty
            String existingSoul = currentSoul != null ? currentSoul : "无现有灵魂设定";
            String existingUserProfile = userProfile != null ? userProfile : "无用户画像";

            // Build prompt
            String prompt = promptTemplate
                    .replace("{current_soul}", existingSoul)
                    .replace("{user_profile}", existingUserProfile)
                    .replace("{conversation_history}", conversationText);

            // Call AI to extract soul
            String updatedSoul = llmCaller.call(user, new Prompt(prompt));

            // Validate and clean soul
            updatedSoul = validateSoul(updatedSoul, jobClawUserId);

            log.info("Soul extracted successfully for user: {} ({} chars)",
                    jobClawUserId, updatedSoul.length());

            return updatedSoul;

        } catch (Exception e) {
            log.error("Failed to extract soul for user: {}", jobClawUserId, e);
            // Fallback to existing soul
            return currentSoul;
        }
    }

    /**
     * Format conversation messages into text for extraction.
     *
     * @param messages conversation messages
     * @return formatted conversation text
     */
    private String formatConversation(List<Message> messages) {
        // Limit to recent messages to avoid overwhelming the model
        List<Message> recentMessages = messages.size() > MAX_CONVERSATION_FOR_EXTRACTION
                ? messages.subList(messages.size() - MAX_CONVERSATION_FOR_EXTRACTION, messages.size())
                : messages;

        return recentMessages.stream()
                .map(msg -> {
                    String role = msg.getMessageType().getValue();
                    String text = msg.getText();
                    // Truncate very long messages
                    if (text.length() > 300) {
                        text = text.substring(0, 300) + "...";
                    }
                    return role.toUpperCase() + ": " + text;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Validate and clean the extracted soul.
     *
     * @param soul raw soul markdown
     * @param jobClawUserId user ID
     * @return validated soul
     */
    private String validateSoul(String soul, String jobClawUserId) {
        if (soul == null || soul.isBlank()) {
            return "";
        }

        // Trim whitespace
        soul = soul.trim();

        // Ensure it starts with # Agent Soul Profile
        if (!soul.startsWith("# Agent Soul Profile")) {
            soul = "# Agent Soul Profile\n\n" + soul;
        }

        // Ensure userId is present
        if (!soul.contains("**userId**")) {
            soul = soul.replace(
                    "## Basic Info",
                    "## Basic Info\n- **userId**: " + jobClawUserId
            );
        }

        // Update timestamp
        soul = soul.replaceAll(
                "- \\*\\*lastUpdated\\*\\*: .+",
                "- **lastUpdated**: " + Instant.now().toString()
        );

        return soul;
    }
}
