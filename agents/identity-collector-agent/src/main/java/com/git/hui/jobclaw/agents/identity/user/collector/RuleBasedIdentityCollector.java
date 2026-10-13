package com.git.hui.jobclaw.agents.identity.user.collector;

import com.git.hui.jobclaw.agents.identity.init.CollectionState;
import com.git.hui.jobclaw.agents.identity.init.InfoCollector;
import com.git.hui.jobclaw.agents.identity.user.UserIdentityManager;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rule-based identity collector using predefined question flow.
 *
 * <p>This implementation uses a fixed set of questions organized by category.
 * It's simple, predictable, and doesn't require AI model calls.
 *
 * <p>Features:
 * <ul>
 *   <li>Predefined question flow (8 questions)</li>
 *   <li>No AI model dependency</li>
 *   <li>Fast and predictable</li>
 *   <li>Allows users to skip questions</li>
 * </ul>
 *
 * AIDEV-NOTE: Rule-based implementation of identityCollector interface
 */
@Component
public class RuleBasedIdentityCollector implements InfoCollector {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedIdentityCollector.class);

    private final UserIdentityManager useridentityManager;
    private final ChannelEventPublisher channelEventPublisher;

    // Track collection states per user
    private final Map<String, CollectionState> collectionStates = new ConcurrentHashMap<>();

    // Predefined question flow
    private static final List<CollectionState.CollectionQuestion> DEFAULT_QUESTION_FLOW = List.of(
            // Basic Info
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.BASIC_INFO,
                    "你好！我是JobClaw助手。为了更好地帮助你，可以告诉我你的姓名吗？😊",
                    "name",
                    false
            ),
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.BASIC_INFO,
                    "你是什么时候毕业的呢？（例如：2026年）",
                    "graduationYear",
                    true
            ),

            // Education
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.EDUCATION,
                    "你在哪所学校就读呢？",
                    "university",
                    true
            ),
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.EDUCATION,
                    "你的专业是什么？",
                    "major",
                    true
            ),

            // Job Preferences
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.JOB_PREFERENCES,
                    "你想找哪个城市的工作呢？（可以说多个城市，用逗号分隔）",
                    "location",
                    true
            ),
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.JOB_PREFERENCES,
                    "你期望的岗位类型是什么？（例如：Java开发、产品经理、数据分析师等）",
                    "jobType",
                    true
            ),
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.JOB_PREFERENCES,
                    "你是在找实习岗位还是正式工作呢？（回复\"实习\"或\"正式\"）",
                    "internship",
                    true
            ),

            // Skills
            new CollectionState.CollectionQuestion(
                    CollectionState.QuestionCategory.SKILLS,
                    "你掌握哪些技术技能呢？（例如：Java、Python、Spring Boot等，可以列出多个）",
                    "skills",
                    false
            )
    );

    public RuleBasedIdentityCollector(
            UserIdentityManager useridentityManager,
            ChannelEventPublisher channelEventPublisher) {
        this.useridentityManager = useridentityManager;
        this.channelEventPublisher = channelEventPublisher;
    }

    @Override
    public AiUserPreferenceProperties.CollectorType getCollectorType() {
        return AiUserPreferenceProperties.CollectorType.RULE_BASED;
    }

    @Override
    public boolean shouldInitiateCollection(String jobClawUserId) {
        // Skip if already has identity
        if (useridentityManager.hasIdentity(jobClawUserId)) {
            return false;
        }

        // Skip if collection already in progress
        CollectionState state = collectionStates.get(jobClawUserId);
        if (state != null && state.isInProgress()) {
            return false;
        }

        return true;
    }

    @Override
    public void initiateCollection(UserConversationInfo userConversationInfo) {
        String jobClawUserId = userConversationInfo.jobClawUserId();
        if (!shouldInitiateCollection(jobClawUserId)) {
            log.debug("Skipping identity collection initiation for user: {}", jobClawUserId);
            return;
        }

        String channel = userConversationInfo.channel();
        String conversationId = userConversationInfo.conversationId();
        log.info("[RuleBased] Initiating collection for user: {} via channel: {}", jobClawUserId, channel);

        // Create collection state
        CollectionState state = new CollectionState(jobClawUserId);
        state.start(channel, conversationId);
        state.setRemainingQuestions(new ArrayList<>(DEFAULT_QUESTION_FLOW));
        collectionStates.put(jobClawUserId, state);

        // Send first question
        askNextQuestion(state, false);
    }

    @Override
    public void processAnswer(UserConversationInfo userConversationInfo, String userMessage, Runnable completeCallback) {
        String jobClawUserId = userConversationInfo.jobClawUserId();
        CollectionState state = collectionStates.get(jobClawUserId);
        if (state == null || !state.isInProgress()) {
            return;
        }

        // Update channel info if changed
        String channel = userConversationInfo.channel();
        String conversationId = userConversationInfo.conversationId();
        state.setActiveChannel(channel);
        state.setConversationId(conversationId);

        // Record answer
        CollectionState.CollectionQuestion currentQ = state.getCurrentQuestion();
        if (currentQ != null) {
            state.recordAnswer(currentQ.field(), userMessage);
            log.info("[RuleBased] Recorded answer for user {}: {} = {}", jobClawUserId, currentQ.field(), userMessage);
        }

        // Check if user wants to skip
        boolean skiped = isSkipMessage(userMessage);

        // Ask next question or complete
        if (state.getRemainingQuestions().isEmpty()) {
            completeCollection(state);
        } else {
            askNextQuestion(state, skiped);
        }
    }

    @Override
    public Optional<CollectionState> getCollectionState(String jobClawUserId) {
        return Optional.ofNullable(collectionStates.get(jobClawUserId));
    }

    /**
     * Ask the next question in the flow
     */
    private void askNextQuestion(CollectionState state, boolean skipped) {
        if (state.getRemainingQuestions().isEmpty()) {
            return;
        }

        CollectionState.CollectionQuestion nextQuestion = state.getRemainingQuestions().get(0);
        state.markQuestionAsked(nextQuestion);

        String question = formatQuestion(nextQuestion, state.getAskedQuestions().size());
        if (skipped) {
            question = "好的，我们跳过这个问题，继续下一个~ 😊\n\n" + question;
        }
        sendProactiveMessage(state, question);

        log.info("[RuleBased] Asked question to user {}: {}", state.getJobClawUserId(), nextQuestion.field());
    }

    /**
     * Format question with progress indicator
     */
    private String formatQuestion(CollectionState.CollectionQuestion question, int progress) {
        return String.format("""
                        【%d/%d】%s
                                        
                        （如果暂时不想回答，可以回复"跳过"或"稍后"）
                        """,
                progress,
                DEFAULT_QUESTION_FLOW.size(),
                question.question()
        );
    }

    /**
     * Complete the collection and create initial identity.md
     */
    private void completeCollection(CollectionState state) {
        log.info("[RuleBased] Completing collection for user: {}", state.getJobClawUserId());

        // Build initial identity from collected answers
        String initialidentity = buildInitialidentity(state);

        // Save identity
        useridentityManager.saveIdentity(state.getJobClawUserId(), initialidentity);

        // Mark as completed
        state.complete();
        collectionStates.remove(state.getJobClawUserId());

        // Send completion message
        String completionMsg = """
                🎉 太好了！我已经了解了你的基本情况。
                                
                现在我可以为你提供更精准的求职推荐啦！
                如果以后有变化，随时告诉我，我会更新你的信息。
                                
                现在，有什么我可以帮你的吗？😊
                """;
        sendProactiveMessage(state, completionMsg);

        log.info("[RuleBased] Collection completed for user: {}", state.getJobClawUserId());
    }

    /**
     * Build initial identity.md from collected answers
     */
    private String buildInitialidentity(CollectionState state) {
        Map<String, String> answers = state.getCollectedAnswers();
        Instant now = Instant.now();

        StringBuilder identity = new StringBuilder();
        identity.append("# User identity Profile\n\n");
        identity.append("## Basic Info\n");
        identity.append("- **jobClawUserId**: ").append(state.getJobClawUserId()).append("\n");
        identity.append("- **lastUpdated**: ").append(now).append("\n");
        identity.append("- **conversationCount**: 1\n");
        identity.append("- **collectedVia**: rule_based\n\n");

        identity.append("## Preferences (偏好)\n");
        identity.append("### Job Preferences (求职偏好)\n");
        identity.append("- **location**: [").append(answers.getOrDefault("location", "")).append("]\n");
        identity.append("- **jobType**: [").append(answers.getOrDefault("jobType", "")).append("]\n");
        identity.append("- **industry**: []\n");
        identity.append("- **salary**: []\n");
        identity.append("- **internship**: ").append("实习".equals(answers.get("internship"))).append("\n\n");

        identity.append("### Communication Preferences (沟通偏好)\n");
        identity.append("- **language**: [中文]\n");
        identity.append("- **detailLevel**: [简洁]\n");
        identity.append("- **responseStyle**: [友好]\n\n");

        identity.append("## Profile (个人特征)\n");
        identity.append("### Education (教育背景)\n");
        identity.append("- **university**: [").append(answers.getOrDefault("university", "")).append("]\n");
        identity.append("- **major**: [").append(answers.getOrDefault("major", "")).append("]\n");
        identity.append("- **graduationYear**: [").append(answers.getOrDefault("graduationYear", "")).append("]\n");
        identity.append("- **degree**: []\n\n");

        identity.append("### Skills (技能)\n");
        identity.append("- **technical**: [").append(answers.getOrDefault("skills", "")).append("]\n");
        identity.append("- **soft**: []\n");
        identity.append("- **languages**: [中文]\n\n");

        identity.append("### Experience (经验)\n");
        identity.append("- **internships**: []\n");
        identity.append("- **projects**: []\n");
        identity.append("- **awards**: []\n\n");

        identity.append("## Key Facts (关键事实)\n");
        if (answers.containsKey("university") && answers.containsKey("graduationYear")) {
            identity.append("- ").append(answers.get("university")).append(answers.get("graduationYear")).append("届毕业生\n");
        }
        if (answers.containsKey("jobType") && answers.containsKey("location")) {
            identity.append("- 寻找").append(answers.get("location")).append("地区").append(answers.get("jobType")).append(
                    "岗位\n");
        }
        identity.append("\n");

        identity.append("## History (历史行为)\n");
        identity.append("### Recent Activities (最近活动)\n");
        identity.append("- ").append(now.toString().substring(0, 10)).append(": 完成初始画像收集\n\n");

        identity.append("### Applied Jobs (投递记录)\n");
        identity.append("- []\n\n");

        identity.append("## Notes (备注)\n");
        identity.append("- 用户通过规则式对话完成初始画像收集\n");

        return identity.toString();
    }

    /**
     * Check if user message indicates skipping
     */
    private boolean isSkipMessage(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase().trim();
        return lower.contains("跳过") || lower.contains("稍后") || lower.contains("不想回答") || lower.equals("skip");
    }

    /**
     * Send skip acknowledgement
     */
    private void sendSkipAcknowledgement(CollectionState state) {
        String msg = "好的，我们跳过这个问题，继续下一个~ 😊";
        sendProactiveMessage(state, msg);
    }

    /**
     * Send proactive message to user
     */
    private void sendProactiveMessage(CollectionState state, String content) {
        try {

            channelEventPublisher.publishProactiveMessage(
                    "identity_" + System.currentTimeMillis(),
                    state.getJobClawUserId(),
                    state.getActiveChannel(),
                    Flux.just(new LlmRspCell(null, content, null))
            );

            log.debug("[RuleBased] Sent proactive message to user: {}", state.getJobClawUserId());
        } catch (Exception e) {
            log.error("[RuleBased] Failed to send proactive message to user: {}", state.getJobClawUserId(), e);
        }
    }
}
