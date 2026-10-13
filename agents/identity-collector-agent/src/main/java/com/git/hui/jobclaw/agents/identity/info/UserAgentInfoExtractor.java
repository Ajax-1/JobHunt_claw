package com.git.hui.jobclaw.agents.identity.info;

import com.git.hui.jobclaw.core.agent.llm.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Extracts and generates agent identity card (info.md) based on user profile and soul.
 *
 * <p>Features:
 * <ul>
 *   <li>Async info generation to avoid blocking</li>
 *   <li>Generates personalized info based on user profile and soul</li>
 *   <li>Fallback to existing info on failure</li>
 *   <li>Prompt template based</li>
 * </ul>
 *
 * AIDEV-NOTE: Info extractor for Phase 2 implementation
 */
@Component
public class UserAgentInfoExtractor {

    private static final Logger log = LoggerFactory.getLogger(UserAgentInfoExtractor.class);

    private final String promptTemplate;

    private final LlmCaller llmCaller;

    public UserAgentInfoExtractor(
            @Value("classpath:/prompts/agent-info-extraction-prompt.md") Resource promptResource,
            LlmCaller simpleLlmCaller) {
        this.llmCaller = simpleLlmCaller;
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("InfoExtractor initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load info extraction prompt template", e);
            throw new RuntimeException("Failed to initialize InfoExtractor", e);
        }
    }

    /**
     * Extract/generate agent info asynchronously.
     *
     * @param user user ID
     * @param currentInfo current info card (may be empty)
     * @param userProfile user profile content (user.md)
     * @param soulProfile soul profile content (soul.md)
     * @return CompletableFuture with updated info markdown
     */
    public CompletableFuture<String> extractAsync(UserConversationInfo user, String currentInfo,
                                                  String userProfile, String soulProfile) {
        return CompletableFuture.supplyAsync(() -> extract(user, currentInfo, userProfile, soulProfile));
    }

    /**
     * Extract/generate agent info synchronously.
     *
     * @param user user ID
     * @param currentInfo current info card (may be empty)
     * @param userProfile user profile content (user.md)
     * @param soulProfile soul profile content (soul.md)
     * @return updated info markdown, or existing info if failed
     */
    public String extract(UserConversationInfo user, String currentInfo, String userProfile, String soulProfile) {
        String jobClawUserId = user.jobClawUserId();
        try {
            log.info("Generating info for user: {}", jobClawUserId);

            // Use existing info or empty
            String existingInfo = currentInfo != null ? currentInfo : "无现有身份名片";
            String existingUserProfile = userProfile != null ? userProfile : "无用户画像";
            String existingSoulProfile = soulProfile != null ? soulProfile : "无灵魂设定";

            // Build prompt
            String prompt = promptTemplate
                    .replace("{current_info}", existingInfo)
                    .replace("{user_profile}", existingUserProfile)
                    .replace("{soul_profile}", existingSoulProfile);

            // Call AI to generate info
            String updatedInfo = llmCaller.call(user, new Prompt(prompt));

            // Validate and clean info
            updatedInfo = validateInfo(updatedInfo, jobClawUserId);

            log.info("Info generated successfully for user: {} ({} chars)",
                    jobClawUserId, updatedInfo.length());

            return updatedInfo;

        } catch (Exception e) {
            log.error("Failed to generate info for user: {}", jobClawUserId, e);
            // Fallback to existing info
            return currentInfo;
        }
    }

    /**
     * Validate and clean the generated info.
     *
     * @param info raw info markdown
     * @param jobClawUserId user ID
     * @return validated info
     */
    private String validateInfo(String info, String jobClawUserId) {
        if (info == null || info.isBlank()) {
            return "";
        }

        // Trim whitespace
        info = info.trim();

        // Ensure it starts with # Agent Identity Card
        if (!info.startsWith("# Agent Identity Card")) {
            info = "# Agent Identity Card\n\n" + info;
        }

        // Ensure userId is present
        if (!info.contains("**userId**")) {
            info = info.replace(
                    "## Basic Info",
                    "## Basic Info\n- **userId**: " + jobClawUserId
            );
        }

        // Update timestamp
        info = info.replaceAll(
                "- \\*\\*lastUpdated\\*\\*: .+",
                "- **lastUpdated**: " + Instant.now().toString()
        );

        return info;
    }
}
