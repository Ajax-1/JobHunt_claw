package com.git.hui.jobclaw.core.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Smart window manager for conversation context.
 *
 * <p>Implements intelligent context window management to prevent token overflow
 * while preserving important conversation history. Features include:
 * <ul>
 *   <li>Configurable window size</li>
 *   <li>Token-based truncation</li>
 *   <li>Short message filtering</li>
 *   <li>Confirmation-only message filtering</li>
 * </ul>
 *
 * <p>AIDEV-NOTE: Core component for Phase 1 smart window implementation
 */
@Component
public class SmartWindowChatMemory {

    private static final Logger log = LoggerFactory.getLogger(SmartWindowChatMemory.class);

    /**
     * Pattern for confirmation-only messages (Chinese and English).
     */
    private static final Pattern CONFIRMATION_PATTERN = Pattern.compile(
            "^(好的|收到|嗯|哦|好|知道了|明白了|ok|OK|yes|YES|是|对)$",
            Pattern.CASE_INSENSITIVE
    );

    private final ContextWindowProperties properties;

    public SmartWindowChatMemory(ContextWindowProperties properties) {
        this.properties = properties;
        log.info("SmartWindowChatMemory initialized with properties: {}", properties);
    }

    /**
     * Manage conversation history according to window configuration.
     *
     * @param messages original message list
     * @return managed message list within window constraints
     */
    public List<Message> manage(List<Message> messages) {
        if (!properties.isTrimEnabled()) {
            log.debug("Smart window is disabled, returning all {} messages", messages.size());
            return messages;
        }

        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        log.debug("Managing context: {} messages received", messages.size());

        List<Message> result = new ArrayList<>(messages);

        // Step 1: Filter short/useless messages
        if (properties.isFilterShortMessages()) {
            result = filterUselessMessages(result);
            log.debug("After filtering: {} messages", result.size());
        }

        // Step 2: Truncate by token count if exceeded
        if (estimateTokens(result) > properties.getMaxTokens()) {
            result = truncateByToken(result);
            log.debug("After token truncation: {} messages, ~{} tokens",
                    result.size(), estimateTokens(result));
        }

        // Step 3: Enforce maximum message count
        if (result.size() > properties.getMaxMessages()) {
            int fromIndex = result.size() - properties.getMaxMessages();
            result = result.subList(fromIndex, result.size());
            log.debug("After message limit: {} messages", result.size());
        }

        return result;
    }

    /**
     * Filter out short and confirmation-only messages.
     *
     * @param messages original messages
     * @return filtered messages
     */
    private List<Message> filterUselessMessages(List<Message> messages) {
        return messages.stream()
                .filter(this::isNotShortMessage)
                .filter(this::isNotConfirmationOnly)
                .collect(Collectors.toList());
    }

    /**
     * Check if message is NOT a short message.
     */
    private boolean isNotShortMessage(Message message) {
        String text = message.getText();
        if (text == null) {
            return false;
        }
        return text.length() > properties.getShortMessageThreshold();
    }

    /**
     * Check if message is NOT a confirmation-only message.
     */
    private boolean isNotConfirmationOnly(Message message) {
        String text = message.getText();
        if (text == null) {
            return true;
        }

        // Only filter user messages for confirmations
        // Keep assistant responses even if short
        if (!(message instanceof UserMessage)) {
            return true;
        }

        String trimmed = text.trim();
        boolean isConfirmation = CONFIRMATION_PATTERN.matcher(trimmed).matches();

        if (isConfirmation) {
            log.debug("Filtered confirmation message: {}", trimmed);
        }

        return !isConfirmation;
    }

    /**
     * Truncate messages by token count, keeping recent messages.
     *
     * @param messages messages to truncate
     * @return truncated message list
     */
    private List<Message> truncateByToken(List<Message> messages) {
        int totalTokens = estimateTokens(messages);
        int maxTokens = properties.getMaxTokens();
        int keepRecent = properties.getKeepRecent();

        if (totalTokens <= maxTokens) {
            return messages;
        }

        List<Message> result = new ArrayList<>(messages);

        // Remove oldest messages until within token limit
        // But always keep at least keepRecent messages
        while (estimateTokens(result) > maxTokens && result.size() > keepRecent) {
            Message removed = result.remove(0);
            log.debug("Removed old message to save ~{} tokens", estimateMessageTokens(removed));
        }

        return result;
    }

    /**
     * Estimate total tokens for a list of messages.
     *
     * @param messages message list
     * @return estimated token count
     */
    public int estimateTokens(List<Message> messages) {
        return messages.stream()
                .mapToInt(this::estimateMessageTokens)
                .sum();
    }

    /**
     * Estimate tokens for a single message.
     *
     * <p>Simple estimation:
     * <ul>
     *   <li>Chinese character: ~2 tokens</li>
     *   <li>English word: ~1.3 tokens</li>
     *   <li>Punctuation/whitespace: ~0.5 tokens</li>
     * </ul>
     *
     * @param message the message
     * @return estimated token count
     */
    public int estimateMessageTokens(Message message) {
        if (message == null || message.getText() == null) {
            return 0;
        }

        String text = message.getText();
        int tokens = 0;

        // Count Chinese characters (approximately 2 tokens each)
        int chineseChars = text.replaceAll("[^\\u4e00-\\u9fa5]", "").length();
        tokens += chineseChars * 2;

        // Count English words (approximately 1.3 tokens each)
        String englishText = text.replaceAll("[\\u4e00-\\u9fa5]", "").trim();
        if (!englishText.isEmpty()) {
            String[] words = englishText.split("\\s+");
            tokens += (int) Math.ceil(words.length * 1.3);
        }

        // Add overhead for message structure (~10 tokens per message)
        tokens += 10;

        return tokens;
    }

    /**
     * Get the window properties.
     */
    public ContextWindowProperties getProperties() {
        return properties;
    }
}
