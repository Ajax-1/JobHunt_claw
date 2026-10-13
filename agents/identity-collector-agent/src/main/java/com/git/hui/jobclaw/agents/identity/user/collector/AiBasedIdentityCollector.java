package com.git.hui.jobclaw.agents.identity.user.collector;

import com.git.hui.jobclaw.agents.identity.init.CollectionState;
import com.git.hui.jobclaw.agents.identity.init.InfoCollector;
import com.git.hui.jobclaw.agents.identity.user.UserIdentityExtractor;
import com.git.hui.jobclaw.agents.identity.user.UserIdentityManager;
import com.git.hui.jobclaw.core.agent.llm.LlmCaller;
import com.git.hui.jobclaw.core.agent.memory.ContextWindowProperties;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-based identity collector using LLM and tool calling.
 *
 * <p>This implementation uses an AI agent with the AskUserQuestionTool to:
 * <ul>
 *   <li>Dynamically generate questions based on conversation context</li>
 *   <li>Adapt questioning strategy based on user responses</li>
 *   <li>Generate comprehensive user profile using AI</li>
 *   <li>Provide more natural, conversational experience</li>
 * </ul>
 *
 * <p>Advantages over rule-based:
 * <ul>
 *   <li>More flexible and adaptive</li>
 *   <li>Can ask follow-up questions</li>
 *   <li>Better at handling unexpected responses</li>
 *   <li>Can infer information from context</li>
 * </ul>
 *
 * AIDEV-NOTE: AI-based implementation of identityCollector interface using LLM + tools
 */
@Component
public class AiBasedIdentityCollector implements InfoCollector {

    private static final Logger log = LoggerFactory.getLogger(AiBasedIdentityCollector.class);

    private final UserIdentityManager useridentityManager;
    private final UserIdentityExtractor useridentityExtractor;
    private final ChannelEventPublisher channelEventPublisher;
    private final ContextWindowProperties contextWindowProperties;

    private final LlmCaller llmCaller;

    // Track collection states per user
    private final Map<String, CollectionState> collectionStates = new ConcurrentHashMap<>();

    // Conversation history per user (for AI context)
    private final Map<String, List<Message>> conversationHistories = new ConcurrentHashMap<>();

    // Conversation turn count per user (to prevent infinite loops)
    private final Map<String, Integer> conversationTurnCounts = new ConcurrentHashMap<>();

    private String promptTemplate;

    // Fixed completion marker to detect end of identity collection
    private static final String IDENTITY_COLLECTION_COMPLETE_MARKER = "[IDENTITY_COLLECTION_COMPLETE]";

    public AiBasedIdentityCollector(UserIdentityManager useridentityManager,
                                    UserIdentityExtractor useridentityExtractor,
                                    ChannelEventPublisher channelEventPublisher,
                                    ContextWindowProperties contextWindowProperties,
                                    LlmCaller simpleLlmCaller,
                                    @Value("classpath:/prompts/user-identity-collect-prompt.md") Resource promptResource) {
        this.useridentityManager = useridentityManager;
        this.useridentityExtractor = useridentityExtractor;
        this.channelEventPublisher = channelEventPublisher;
        this.contextWindowProperties = contextWindowProperties;
        this.llmCaller = simpleLlmCaller;
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("AiBasedIdentityCollector initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load user identity collection prompt template", e);
            throw new RuntimeException("Failed to initialize AiBasedIdentityCollector", e);
        }
    }

    @Override
    public AiUserPreferenceProperties.CollectorType getCollectorType() {
        return AiUserPreferenceProperties.CollectorType.AI_BASED;
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
        String channel = userConversationInfo.channel();
        if (!shouldInitiateCollection(jobClawUserId)) {
            log.debug("[UserIdentity] Skipping collection initiation for user: {}", jobClawUserId);
            return;
        }

        log.info("[UserIdentity] Initiating AI-driven collection for user: {} via channel: {}", jobClawUserId, channel);

        // Create collection state
        CollectionState state = new CollectionState(jobClawUserId);
        state.start(channel, userConversationInfo.conversationId());
        collectionStates.put(jobClawUserId, state);

        // Initialize conversation history
        conversationHistories.put(jobClawUserId, new ArrayList<>());

        // Initialize turn counter
        conversationTurnCounts.put(jobClawUserId, 0);

        // Start AI-driven conversation
        startAiConversation(state);
    }

    @Override
    public void processAnswer(UserConversationInfo userConversationInfo, String userMessage, Runnable completeCallback) {
        String jobClawUserId = userConversationInfo.jobClawUserId();
        CollectionState state = collectionStates.get(jobClawUserId);
        if (state == null || !state.isInProgress()) {
            return;
        }

        // Update channel info
        state.setActiveChannel(userConversationInfo.channel());
        state.setConversationId(userConversationInfo.conversationId());

        // Add user message to history
        List<Message> history = conversationHistories.get(jobClawUserId);
        history.add(new UserMessage(userMessage));

        // Increment turn counter
        int currentTurns = conversationTurnCounts.getOrDefault(jobClawUserId, 0) + 1;
        conversationTurnCounts.put(jobClawUserId, currentTurns);

        log.info("[UserIdentity] User answered: {} (user={}, turn={})", userMessage, jobClawUserId, currentTurns);

        // Check if exceeded max turns
        if (currentTurns >= contextWindowProperties.getMaxIdentityCollectionTurns()) {
            log.warn("[UserIdentity] Max conversation turns ({}) reached for user: {}, forcing completion",
                    contextWindowProperties.getMaxIdentityCollectionTurns(), jobClawUserId);
            completeCollection(state, history, completeCallback);
            return;
        }

        // Continue AI conversation
        continueAiConversation(state, history, completeCallback);
    }

    @Override
    public Optional<CollectionState> getCollectionState(String jobClawUserId) {
        return Optional.ofNullable(collectionStates.get(jobClawUserId));
    }

    /**
     * Start AI-driven conversation using LLM with streaming
     */
    private void startAiConversation(CollectionState state) {
        log.info("[UserIdentity] Starting AI conversation for user: {}", state.getJobClawUserId());

        // Create system prompt with completion marker instruction
        String systemPrompt = promptTemplate + "\n\n当你认为已经获取到必要的信息，并且无需再次问答收集信息时，你需要直接总结你收集到的信息展示给用户，并在最后单独一行添加标记：[IDENTITY_COLLECTION_COMPLETE]";

        // Create initial user message to trigger AI
        String initialPrompt = """
                用户刚刚开始对话，还没有任何画像信息。
                请友好地自我介绍，然后开始按照画像模板收集信息。
                                
                你需要收集的字段优先级：
                1. 【必填】毕业年份、学校、专业、期望城市、岗位类型、实习/正式
                2. 【选填】姓名、学历、期望行业、薪资、技术技能、实习经历、项目经历
                                
                请从最基础的信息开始（比如毕业年份和学校），一次只问一个问题。
                语气要友好自然，像朋友聊天，不要像审问。
                """;

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(initialPrompt));

        // Call AI and stream response
        Flux<LlmRspCell> responseFlux = callAiForCollectionStream(state, systemPrompt, messages);
        sendProactiveMessageStream(state, responseFlux);
    }

    /**
     * Continue AI conversation after user response with streaming
     */
    private void continueAiConversation(CollectionState state, List<Message> history, Runnable completeCallback) {
        String systemPrompt = promptTemplate + "\n\n当你认为已经获取到必要的信息，并且无需再次问答收集信息时，你需要直接总结你收集到的信息展示给用户，并在最后单独一行添加标记：[IDENTITY_COLLECTION_COMPLETE]";

        // Call AI and stream response
        Flux<LlmRspCell> responseFlux = callAiForCollectionStream(state, systemPrompt, history);

        // Send streaming response and check for completion marker
        sendProactiveMessageStreamWithCompletionCheck(state, responseFlux, history, completeCallback);
    }


    /**
     * Complete collection and generate identity.md
     */
    private void completeCollection(CollectionState state, List<Message> history, Runnable completeCallback) {
        log.info("[UserIdentity] Completing AI-driven collection for user: {}", state.getJobClawUserId());

        // Use UserIdentityExtractor to generate identity.md from conversation
        String identity = useridentityExtractor.extract(buildUserConversationInfo(state), "", history);

        if (identity == null || identity.isBlank()) {
            // Fallback to simple identity
            identity = buildSimpleIdentity(state);
        }

        // Save identity
        useridentityManager.saveIdentity(state.getJobClawUserId(), identity);

        // Mark as completed
        state.complete();
        collectionStates.remove(state.getJobClawUserId());
        conversationHistories.remove(state.getJobClawUserId());
        conversationTurnCounts.remove(state.getJobClawUserId());

        // Send completion message with streaming
        String completionMsg = """
                🎉 太好了！通过刚才的聊天，我已经对你有了基本了解。
                                
                现在我可以为你提供更精准的求职推荐啦！
                如果以后有变化，随时告诉我，我会更新你的信息。
                                
                现在，有什么我可以帮你的吗？😊
                """;
        sendProactiveMessageStream(state, Flux.just(new LlmRspCell(null, completionMsg, null)));
        if (completeCallback != null) {
            completeCallback.run();
        }
        log.info("[UserIdentity] AI-driven collection completed for user: {}", state.getJobClawUserId());
    }

    /**
     * Build simple identity as fallback
     */
    private String buildSimpleIdentity(CollectionState state) {
        Instant now = Instant.now();
        return """
                # User identity Profile
                                
                ## Basic Info
                - **jobClawUserId**: %s
                - **lastUpdated**: %s
                - **conversationCount**: 1
                - **collectedVia**: ai_based
                                
                ## Notes (备注)
                - 用户通过AI对话完成初始画像收集
                - 详细信息请查看对话历史
                """.formatted(state.getJobClawUserId(), now);
    }


    /**
     * Call AI for collection with streaming response
     * <p>
     * 使用流式调用，实时返回文本内容，减少用户等待时间。
     * 通过检测固定标记 [IDENTITY_COLLECTION_COMPLETE] 来判断收集是否完成。
     */
    private Flux<LlmRspCell> callAiForCollectionStream(CollectionState state, String systemPrompt, List<Message> messages) {
        String jobClawUserId = state.getJobClawUserId();
        try {
            // Build conversation with system prompt
            List<Message> conversationWithSystem = new ArrayList<>();
            conversationWithSystem.add(new SystemMessage(systemPrompt));
            conversationWithSystem.addAll(messages);

            Prompt prompt = Prompt.builder()
                    .messages(conversationWithSystem)
                    .build();


            UserConversationInfo userConversationInfo = buildUserConversationInfo(state);
            log.debug("[UserIdentity] Calling AI with streaming for user: {}", jobClawUserId);
            return llmCaller.stream(userConversationInfo, prompt, LlmRspCell::of)
                    .doOnError(error -> {
                        log.error("[UserIdentity] Streaming error for user: {}", jobClawUserId, error);
                    });
        } catch (Exception e) {
            log.error("[UserIdentity] Failed to call AI for user: {}", jobClawUserId, e);
            return Flux.just(new LlmRspCell(null, "现在大模型响应出了点问题，请稍后再试~", null));
        }
    }


    /**
     * Send proactive message with streaming and check for completion marker
     *
     * @param state collection state
     * @param contentFlux streaming content flux
     */
    private void sendProactiveMessageStreamWithCompletionCheck(CollectionState state, Flux<LlmRspCell> contentFlux, List<Message> history, Runnable completeCallback) {
        // Accumulate content to check for completion marker
        StringBuilder contentAccumulator = new StringBuilder();
        String jobClawUserId = state.getJobClawUserId();

        // Create a flux that monitors for completion marker while streaming
        Flux<LlmRspCell> monitoredFlux = contentFlux
                .doOnNext(chunk -> {
                    if (chunk.content() != null) {
                        contentAccumulator.append(chunk.content());
                    }
                })
                .doOnComplete(() -> {
                    // When streaming is complete, add AI's response to conversation history
                    String fullResponse = contentAccumulator.toString();
                    if (!fullResponse.isBlank()) {
                        history.add(new AssistantMessage(fullResponse));

                        // Check if completion marker is present
                        if (contentAccumulator.toString().contains(IDENTITY_COLLECTION_COMPLETE_MARKER)) {
                            log.info("[UserIdentity] Completion marker detected for user: {}", jobClawUserId);
                            completeCollection(state, conversationHistories.get(jobClawUserId), completeCallback);
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("[UserIdentity] Streaming error for user: {}", jobClawUserId, error);
                });

        sendProactiveMessageStream(state, monitoredFlux);
    }

    /**
     * Send proactive message to user with streaming
     *
     * @param state collection state
     * @param contentFlux streaming content flux
     */
    private void sendProactiveMessageStream(CollectionState state, Flux<LlmRspCell> contentFlux) {
        String responseId = "identity_AI_" + System.currentTimeMillis();

        channelEventPublisher.publishProactiveMessage(
                responseId,
                state.getJobClawUserId(),
                state.getActiveChannel(),
                contentFlux
        );

        log.debug("[UserIdentity] Sent streaming proactive message to user: {}, responseId={}",
                state.getJobClawUserId(), responseId);
    }
}
