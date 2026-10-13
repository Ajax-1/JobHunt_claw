package com.git.hui.jobclaw.agents.identity.soul;

import com.git.hui.jobclaw.agents.identity.init.CollectionState;
import com.git.hui.jobclaw.agents.identity.init.InfoCollector;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-based soul collector using LLM to collect AI personality preferences.
 *
 * <p>This implementation uses an AI agent to:
 * <ul>
 *   <li>Dynamically generate questions about personality preferences</li>
 *   <li>Adapt questioning strategy based on user responses</li>
 *   <li>Collect communication style, tone, and relationship preferences</li>
 *   <li>Generate comprehensive soul.md using SoulExtractor</li>
 * </ul>
 *
 * AIDEV-NOTE: AI-based soul collector for Phase 2 implementation
 */
@Component
public class UserAgentSoulCollector implements InfoCollector {

    private static final Logger log = LoggerFactory.getLogger(UserAgentSoulCollector.class);

    private final UserAgentSoulManager soulManager;
    private final UserAgentSoulExtractor userAgentSoulExtractor;
    private final ChannelEventPublisher channelEventPublisher;
    private final ContextWindowProperties contextWindowProperties;

    private final LlmCaller llmCaller;

    // Track collection states per user
    private final Map<String, CollectionState> collectionStates = new ConcurrentHashMap<>();

    // Conversation history per user (for AI context)
    private final Map<String, List<Message>> conversationHistories = new ConcurrentHashMap<>();

    // Conversation turn count per user (to prevent infinite loops)
    private final Map<String, Integer> conversationTurnCounts = new ConcurrentHashMap<>();

    // Prompt template
    private String promptTemplate;

    // Fixed completion marker to detect end of soul collection
    private static final String SOUL_COLLECTION_COMPLETE_MARKER = "[SOUL_COLLECTION_COMPLETE]";

    public UserAgentSoulCollector(UserAgentSoulManager soulManager,
                                  UserAgentSoulExtractor userAgentSoulExtractor,
                                  ChannelEventPublisher channelEventPublisher,
                                  ContextWindowProperties contextWindowProperties,
                                  LlmCaller simpleLlmCaller,
                                  @Value("classpath:/prompts/agent-soul-collect-prompt.md") Resource promptResource) {
        this.soulManager = soulManager;
        this.userAgentSoulExtractor = userAgentSoulExtractor;
        this.channelEventPublisher = channelEventPublisher;
        this.contextWindowProperties = contextWindowProperties;
        this.llmCaller = simpleLlmCaller;

        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("UserAgentSoulCollector initialized with prompt template");
        } catch (IOException e) {
            log.error("Failed to load soul collection prompt template", e);
            throw new RuntimeException("Failed to initialize UserAgentSoulCollector", e);
        }
    }

    @Override
    public AiUserPreferenceProperties.CollectorType getCollectorType() {
        return AiUserPreferenceProperties.CollectorType.AI_BASED;
    }

    @Override
    public boolean shouldInitiateCollection(String jobClawUserId) {
        // Skip if already has soul
        if (soulManager.hasSoul(jobClawUserId)) {
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
            log.debug("[Soul] Skipping collection initiation for user: {}", jobClawUserId);
            return;
        }

        log.info("[Soul] Initiating AI-driven soul collection for user: {} via channel: {}",
                jobClawUserId, channel);

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
        var jobClawUserId = userConversationInfo.jobClawUserId();
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

        log.info("[Soul] User answered: {} (user={}, turn={})", userMessage, jobClawUserId, currentTurns);

        // Check if exceeded max turns
        if (currentTurns >= contextWindowProperties.getMaxSoulCollectionTurns()) {
            log.warn("[Soul] Max conversation turns ({}) reached for user: {}, forcing completion",
                    contextWindowProperties.getMaxSoulCollectionTurns(), jobClawUserId);
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
        log.info("[Soul] Starting AI conversation for user: {}", state.getJobClawUserId());

        // Create initial user message to trigger AI
        String initialPrompt = """
                用户刚刚开始对话,还没有设置AI人格。
                请友好地自我介绍,然后开始收集用户期望的AI人格信息。
                                
                你需要收集的字段优先级:
                1. 【必填】agentName, communication_style, emotional_tone, proactive_level, detail_orientation, relationship_type
                2. 【选填】formality_level, humor_frequency, values
                                
                请从基础信息开始(比如给AI起个名字),一次的问题不要超过三个
                语气要友好自然,像朋友聊天,不要像审问。

                重要：当你认为已经获取到必要的信息，并且无需再次问答收集信息时，你需要直接根据用户的的输入，来总结你收集到的信息，并展示给用户，不需要再次提问
                且在回复的最后一行添加标记
                [SOUL_COLLECTION_COMPLETE]
                """;

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(initialPrompt));

        // Call AI and stream response
        Flux<LlmRspCell> responseFlux = callAiForCollectionStream(state, messages);
        sendProactiveMessageStream(state, responseFlux);
    }

    /**
     * Continue AI conversation after user response with streaming
     */
    private void continueAiConversation(CollectionState state, List<Message> history, Runnable completeCallback) {
        // Call AI and stream response
        Flux<LlmRspCell> responseFlux = callAiForCollectionStream(state, history);

        // Send streaming response and check for completion marker
        sendProactiveMessageStreamWithCompletionCheck(state, responseFlux, history, completeCallback);
    }

    /**
     * Call AI for collection with streaming response
     * <p>
     * 使用流式调用，实时返回文本内容，减少用户等待时间。
     * 通过检测固定标记 [SOUL_COLLECTION_COMPLETE] 来判断收集是否完成。
     */
    private Flux<LlmRspCell> callAiForCollectionStream(CollectionState state, List<Message> history) {
        try {
            // Build system prompt
            String systemPrompt = promptTemplate + """
                    \n\n
                    重要：当你认为已经获取到必要的信息，并且无需再次问答收集信息时，你需要直接根据用户的的输入，来总结你收集到的信息，并展示给用户
                    且在回复的最后一行添加标记
                    [SOUL_COLLECTION_COMPLETE]
                    """;

            // Create messages list with system prompt
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(history);
            Prompt prompt = new Prompt(messages);

            log.debug("[Soul] Calling AI with streaming for user: {}", state.getJobClawUserId());
            // Call AI model with streaming
            return llmCaller.stream(buildUserConversationInfo(state), prompt, LlmRspCell::of)
                    .doOnError(error -> {
                        log.error("[Soul] Streaming error for user: {}", state.getJobClawUserId(), error);
                    });
        } catch (Exception e) {
            log.error("[Soul] Failed to call AI for collection: {}", state.getJobClawUserId(), e);
            return Flux.error(e);
        }
    }

    /**
     * Complete the soul collection and generate soul.md
     */
    private void completeCollection(CollectionState state, List<Message> history, Runnable completeCallback) {
        log.info("[Soul] Collection completed for user: {}, generating soul.md", state.getJobClawUserId());

        try {
            // Extract soul from conversation history
            String soulMd = userAgentSoulExtractor.extract(
                    buildUserConversationInfo(state),
                    "", // No existing soul
                    history,
                    "" // No user profile yet
            ); // Wait for completion

            // Save soul.md
            if (soulMd != null && !soulMd.isBlank()) {
                soulManager.saveSoul(state.getJobClawUserId(), soulMd);
                log.info("[Soul] soul.md saved for user: {}", state.getJobClawUserId());

                // Send completion message with streaming
                sendProactiveMessageStream(state,
                        Flux.just(new LlmRspCell(null, "太好了!我已经了解你期望的AI人格了。接下来我会进一步了解你的基本信息,以便更好地帮助你~", null)));
            }

            // Mark state as completed
            state.complete();

            // Clean up resources
            collectionStates.remove(state.getJobClawUserId());
            conversationHistories.remove(state.getJobClawUserId());
            conversationTurnCounts.remove(state.getJobClawUserId());

            // 执行回调
            if (completeCallback != null) {
                completeCallback.run();
            }
        } catch (Exception e) {
            log.error("[Soul] Failed to complete collection for user: {}", state.getJobClawUserId(), e);
            sendProactiveMessageStream(state,
                    Flux.just(new LlmRspCell(null, "生成人格设定时出了点问题,但不影响后续对话,我们继续吧~", null)));
            state.complete();

            // Clean up resources even on error
            collectionStates.remove(state.getJobClawUserId());
            conversationHistories.remove(state.getJobClawUserId());
            conversationTurnCounts.remove(state.getJobClawUserId());
        }
    }

    /**
     * Send proactive message to user with streaming
     *
     * @param state collection state
     * @param contentFlux streaming content flux
     */
    private void sendProactiveMessageStream(CollectionState state, Flux<LlmRspCell> contentFlux) {
        String responseId = "SOUL_" + System.currentTimeMillis();

        channelEventPublisher.publishProactiveMessage(
                responseId,
                state.getJobClawUserId(),
                state.getActiveChannel(),
                contentFlux
        );

        log.debug("[Soul] Sent streaming proactive message to user: {}, responseId={}",
                state.getJobClawUserId(), responseId);
    }

    /**
     * Send proactive message with streaming and check for completion marker
     *
     * @param state collection state
     * @param contentFlux streaming content flux
     */
    private void sendProactiveMessageStreamWithCompletionCheck(CollectionState state,
                                                               Flux<LlmRspCell> contentFlux,
                                                               List<Message> history,
                                                               Runnable completeCallback) {
        // Accumulate content to check for completion marker
        StringBuilder contentAccumulator = new StringBuilder();
        String jobClawUserId = state.getJobClawUserId();

        // Create a flux that monitors for completion marker while streaming
        Flux<LlmRspCell> monitoredFlux = contentFlux
                .doOnNext(chunk -> {
                    if (chunk.content() != null) {
                        contentAccumulator.append(chunk.content());

                        // Check if completion marker is present
                        if (contentAccumulator.toString().contains(SOUL_COLLECTION_COMPLETE_MARKER)) {
                            log.info("[Soul] Completion marker detected for user: {}", jobClawUserId);
                            completeCollection(state, conversationHistories.get(jobClawUserId), completeCallback);
                        }
                    }
                })
                .doOnComplete(() -> {
                    // When streaming is complete, add AI's response to conversation history
                    String fullResponse = contentAccumulator.toString();
                    if (!fullResponse.isBlank()) {
                        history.add(new AssistantMessage(fullResponse));
                    }
                })
                .doOnError(error -> {
                    log.error("[Soul] Streaming error for user: {}", jobClawUserId, error);
                });

        sendProactiveMessageStream(state, monitoredFlux);
    }
}
