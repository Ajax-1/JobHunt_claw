package com.git.hui.jobclaw.core.monitor;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.agent.react.ReActMiddleware;
import com.git.hui.jobclaw.core.cache.LocalCacheManager;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class LlmMonitorMiddleware implements ReActMiddleware {

    private static final String CACHE_NAME = "llmMonitorState";

    private final ApplicationEventPublisher publisher;
    private final MeterRegistry meterRegistry;
    private final LocalCacheManager cacheManager;
    private final ModelProviders modelProviders;

    public LlmMonitorMiddleware(ApplicationEventPublisher publisher, MeterRegistry meterRegistry,
                                LocalCacheManager cacheManager, ModelProviders modelProviders) {
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
        this.cacheManager = cacheManager;
        this.modelProviders = modelProviders;
        cacheManager.getCache(CACHE_NAME, Duration.ofMinutes(30), 5000);
    }

    @Override
    public void setContext(ChatClientRequest request, String chatId) {
        UserConversationInfo user = extractUser(request);
        String operation = user != null && user.agent() != null ? "agent_chat" : "core_call";
        LlmCallContext context = user != null
                ? LlmCallContext.of(chatId, user, operation, chatId.startsWith("A-") ? "ASYNC" : "SYNC")
                : new LlmCallContext(chatId, "unknown", "unknown",
                "unknown", "unknown", operation, chatId.startsWith("A-") ? "ASYNC" : "SYNC");
        MonitorState state = new MonitorState(context);
        state.modelInfo = user != null ? modelProviders.currentModelInfo(user.jobClawUserId()) : null;
        cacheManager.put(CACHE_NAME, chatId, state);
    }

    @Override
    public void beforeReasoning(List<Message> messages, int iter, String chatId) {
        MonitorState state = cacheManager.get(CACHE_NAME, chatId);
        if (state == null) return;
        state.requestStartNanos = System.nanoTime();
        state.pendingPromptText = messages.stream()
                .map(Message::getText)
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
        // 获取最后一个 UserMessage, 来提取用户的提问
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof UserMessage) {
                state.userInput = message.getText();
                break;
            }
        }
    }

    @Override
    public void afterReasoning(ChatResponse response, int iter, String chatId) {
        MonitorState state = cacheManager.get(CACHE_NAME, chatId);
        if (state == null) return;

        long start = state.requestStartNanos;
        if (start == 0) return;
        long duration = (System.nanoTime() - start) / 1_000_000;

        state.requests.incrementAndGet();
        state.duration.addAndGet(duration);

        Long inputTokens = null, outputTokens = null, totalTokens = null;
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            inputTokens = usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue();
            outputTokens = usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().longValue();
            totalTokens = usage.getTotalTokens() == null ? null : usage.getTotalTokens().longValue();
            state.usageKnown.set(true);
            state.inputTokens.addAndGet(inputTokens == null ? 0 : inputTokens);
            state.outputTokens.addAndGet(outputTokens == null ? 0 : outputTokens);
            state.totalTokens.addAndGet(totalTokens == null ? 0 : totalTokens);
        }

        ModelConfig.ModelInfo modelInfo = state.modelInfo;
        String provider = modelInfo == null ? "unknown" : modelInfo.getProvider();
        String model = modelInfo == null ? "unknown" : modelInfo.getModelName();
        String modelType = modelInfo == null || modelInfo.getType() == null ? "CHAT" : modelInfo.getType().name();
        BigDecimal cost = calculateCost(modelInfo, inputTokens, outputTokens);
        state.addCost(cost);

        LlmCallContext c = state.context;
        String promptText = state.userInput;
        String responseText = response != null && response.getResult() != null
                ? response.getResult().getOutput().getText() : null;
        LlmRequestRecord record = new LlmRequestRecord(UUID.randomUUID().toString(), c.invocationId(), c.jobClawUserId(),
                c.conversationId(), c.channel(), c.agent(), c.operation(), c.mode(), provider, model, modelType,
                "SUCCESS", duration, inputTokens, outputTokens, totalTokens, cost, null,
                sample(promptText, "SUCCESS"), sample(responseText, "SUCCESS"),
                Instant.now());
        safePublish(record);

        meterRegistry.counter("jobclaw.llm.requests", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", "SUCCESS").increment();
        Timer.builder("jobclaw.llm.request.duration").tags("agent", tag(c.agent()), "operation", tag(c.operation()),
                        "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", "SUCCESS")
                .register(meterRegistry).record(java.time.Duration.ofMillis(duration));
        if (totalTokens != null) meterRegistry.counter("jobclaw.llm.tokens", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", "SUCCESS").increment(totalTokens);
        if (cost != null) meterRegistry.counter("jobclaw.llm.estimated.cost", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", "SUCCESS").increment(cost.doubleValue());
    }

    @Override
    public void onComplete(int totalIters, String finalResponse, String chatId) {
        MonitorState state = cacheManager.get(CACHE_NAME, chatId);
        if (state == null) return;
        cacheManager.remove(CACHE_NAME, chatId);
        publishInvocation(state, "SUCCESS", null);
        modelProviders.clearCurrentModelInfo(state.context.jobClawUserId());
    }

    @Override
    public void onError(Exception error, int iter, String chatId) {
        MonitorState state = cacheManager.get(CACHE_NAME, chatId);
        if (state == null) return;
        cacheManager.remove(CACHE_NAME, chatId);
        if (state.requestStartNanos != 0) {
            publishFailedRequest(state, error);
        }
        publishInvocation(state, "FAILED", error);
        modelProviders.clearCurrentModelInfo(state.context.jobClawUserId());
    }

    private void publishFailedRequest(MonitorState state, Throwable error) {
        long duration = (System.nanoTime() - state.requestStartNanos) / 1_000_000;
        state.requests.incrementAndGet();
        state.duration.addAndGet(duration);

        ModelConfig.ModelInfo modelInfo = state.modelInfo;
        String provider = modelInfo == null ? "unknown" : modelInfo.getProvider();
        String model = modelInfo == null ? "unknown" : modelInfo.getModelName();
        String modelType = modelInfo == null || modelInfo.getType() == null ? "CHAT" : modelInfo.getType().name();

        LlmCallContext c = state.context;
        LlmRequestRecord record = new LlmRequestRecord(UUID.randomUUID().toString(), c.invocationId(), c.jobClawUserId(),
                c.conversationId(), c.channel(), c.agent(), c.operation(), c.mode(), provider, model, modelType,
                "FAILED", duration, null, null, null, null, error.getMessage(),
                sample(state.pendingPromptText, "FAILED"), null,
                Instant.now());
        safePublish(record);

        meterRegistry.counter("jobclaw.llm.requests", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", "FAILED").increment();
        Timer.builder("jobclaw.llm.request.duration").tags("agent", tag(c.agent()), "operation", tag(c.operation()),
                        "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", "FAILED")
                .register(meterRegistry).record(java.time.Duration.ofMillis(duration));
    }

    private void publishInvocation(MonitorState state, String outcome, Throwable error) {
        if (!state.published.compareAndSet(0, 1)) return;
        LlmCallContext c = state.context;
        safePublish(new LlmInvocationRecord(c.invocationId(), c.jobClawUserId(), c.conversationId(), c.channel(),
                c.agent(), c.operation(), c.mode(), outcome, state.duration.get(), state.requests.get(),
                state.usageKnown.get() ? state.inputTokens.get() : null,
                state.usageKnown.get() ? state.outputTokens.get() : null,
                state.usageKnown.get() ? state.totalTokens.get() : null,
                state.cost, error == null ? null : error.getMessage(), state.started));
        meterRegistry.counter("jobclaw.llm.invocations", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "mode", tag(c.mode()), "outcome", outcome).increment();
    }

    private UserConversationInfo extractUser(ChatClientRequest request) {
        ChatOptions options = request.prompt().getOptions();
        if (options instanceof ToolCallingChatOptions tco) {
            var ctx = tco.getToolContext();
            if (ctx != null) {
                Object user = ctx.get("user");
                if (user instanceof UserConversationInfo u) return u;
            }
        }
        return null;
    }

    private void safePublish(Object event) {
        try {
            publisher.publishEvent(event);
        } catch (Exception e) {
            meterRegistry.counter("jobclaw.llm.telemetry.dropped").increment();
            log.warn("Failed to publish LLM monitor event", e);
        }
    }

    private String sample(String text, String outcome) {
        if (text == null) return null;
        String cleaned = text.replaceAll("(?i)Bearer\\s+\\S+", "Bearer ***")
                .replaceAll("[\\w.+-]+@[\\w.-]+", "***@***")
                .replaceAll("1[3-9]\\d{9}", "***")
                .replaceAll("\\b\\d{17}[\\dXx]\\b", "***");
        final int size = ("FAILED".equals(outcome) || Math.random() <= 0.01) ? 4000 : 100;
        return cleaned.substring(0, Math.min(cleaned.length(), size));
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private BigDecimal calculateCost(ModelConfig.ModelInfo info, Long input, Long output) {
        if (info == null || (input == null && output == null)) return null;
        BigDecimal cost = BigDecimal.ZERO;
        boolean known = false;
        if (input != null && info.getInputPricePerMillionTokens() != null) {
            cost = cost.add(info.getInputPricePerMillionTokens().multiply(BigDecimal.valueOf(input)));
            known = true;
        }
        if (output != null && info.getOutputPricePerMillionTokens() != null) {
            cost = cost.add(info.getOutputPricePerMillionTokens().multiply(BigDecimal.valueOf(output)));
            known = true;
        }
        return known ? cost.divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP) : null;
    }

    private static class MonitorState {
        final LlmCallContext context;
        final Instant started = Instant.now();
        final AtomicInteger requests = new AtomicInteger();
        final AtomicLong duration = new AtomicLong();
        final AtomicLong inputTokens = new AtomicLong();
        final AtomicLong outputTokens = new AtomicLong();
        final AtomicLong totalTokens = new AtomicLong();
        final AtomicBoolean usageKnown = new AtomicBoolean();
        BigDecimal cost;
        final AtomicInteger published = new AtomicInteger();
        volatile long requestStartNanos;
        String pendingPromptText;

        // 最后一个UserMessage, 用于表示用户的提问
        String userInput;
        ModelConfig.ModelInfo modelInfo;

        MonitorState(LlmCallContext context) {
            this.context = context;
        }

        synchronized void addCost(BigDecimal value) {
            if (value != null) cost = cost == null ? value : cost.add(value);
        }
    }
}
