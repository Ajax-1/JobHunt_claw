package com.git.hui.jobclaw.agents.identity.user;

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
 * Extracts and updates user identity/profile from conversation history.
 *
 * <p>Features:
 * <ul>
 *   <li>Async identity extraction to avoid blocking</li>
 *   <li>Incremental update based on existing identity</li>
 *   <li>Fallback to existing identity on failure</li>
 *   <li>Prompt template based</li>
 * </ul>
 *
 */
@Component
public class UserIdentityExtractor {

    private static final Logger log = LoggerFactory.getLogger(UserIdentityExtractor.class);

    private static final int MAX_CONVERSATION_FOR_EXTRACTION = 50;
    private final String promptTemplate;

    private final LlmCaller llmCaller;

    public UserIdentityExtractor(
            @Value("classpath:/prompts/user-identity-extraction-prompt.md") Resource promptResource, LlmCaller simpleLlmCaller) {
        this.llmCaller = simpleLlmCaller;
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("UserIdentityExtractor initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load user identity extraction prompt template", e);
            throw new RuntimeException("Failed to initialize UseridentityExtractor", e);
        }
    }

    /**
     * Extract user identity asynchronously.
     *
     * @param userConversationInfo user ID
     * @param currentIdentity current identity profile (may be empty)
     * @param messages new conversation messages
     * @return CompletableFuture with updated identity markdown
     */
    public CompletableFuture<String> extractAsync(UserConversationInfo userConversationInfo, String currentIdentity, List<Message> messages) {
        return CompletableFuture.supplyAsync(() -> extract(userConversationInfo, currentIdentity, messages));
    }

    /**
     * Extract user identity synchronously.
     *
     * @param userConversationInfo user ID
     * @param currentIdentity current identity profile (may be empty)
     * @param messages new conversation messages
     * @return updated identity markdown, or existing identity if failed
     */
    public String extract(UserConversationInfo userConversationInfo, String currentIdentity, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("No messages to extract identity from");
            return "";
        }

        String jobClawUserId = userConversationInfo.jobClawUserId();
        try {
            log.info("Extracting identity for user: {} ({} messages)", jobClawUserId, messages.size());

            // Prepare conversation text
            String conversationText = formatConversation(messages);

            // Use existing identity or empty
            String existingIdentity = currentIdentity != null ? currentIdentity : "无现有画像";

            // Build prompt
            String prompt = promptTemplate
                    .replace("{current_soul}", existingIdentity)
                    .replace("{conversation_history}", conversationText);
            prompt += "\n jobClawUserId = " + userConversationInfo.jobClawUserId();

            // Call AI to extract identity
            String updatedIdentity = llmCaller.call(userConversationInfo, new Prompt(prompt));

            // Validate and clean identity
            updatedIdentity = validateidentity(updatedIdentity, jobClawUserId);

            log.info("identity extracted successfully for user: {} ({} chars)",
                    jobClawUserId, updatedIdentity.length());

            return updatedIdentity;

        } catch (Exception e) {
            log.error("Failed to extract identity for user: {}", jobClawUserId, e);
            // Fallback to existing identity
            return currentIdentity;
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
     * Validate and clean the extracted identity.
     *
     * @param identity raw identity markdown
     * @param jobClawUserId user ID
     * @return validated identity
     */
    private String validateidentity(String identity, String jobClawUserId) {
        if (identity == null || identity.isBlank()) {
            return "";
        }

        // Trim whitespace
        identity = identity.trim();

        // 如果 identity 是markdown格式，需要进行格式化
        if (identity.startsWith("```")) {
            // 移除首行
            identity = identity.substring(identity.indexOf("\n") + 1);
        }
        if (identity.endsWith("```")) {
            // 移除尾行
            identity = identity.substring(0, identity.length() - 3);
        }

        // Ensure it starts with # User identity Profile
        if (!identity.startsWith("# User identity Profile")) {
            identity = "# User identity Profile\n\n" + identity;
        }

        // Ensure jobClawUserId is present
        if (!identity.contains("**jobClawUserId**")) {
            identity = identity.replace(
                    "## Basic Info",
                    "## Basic Info\n- **jobClawUserId**: " + jobClawUserId
            );
        }

        // Update timestamp
        identity = identity.replaceAll(
                "- \\*\\*lastUpdated\\*\\*: .+",
                "- **lastUpdated**: " + Instant.now().toString()
        );

        return identity;
    }
}
