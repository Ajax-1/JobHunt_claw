package com.git.hui.jobclaw.core.monitor.del;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.git.hui.jobclaw.core.monitor.LlmCallContext;
import com.git.hui.jobclaw.core.monitor.LlmInvocationRecord;
import com.git.hui.jobclaw.core.monitor.LlmRequestRecord;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * DefaultLlmMonitor 类实现了 LlmMonitor 接口，用于监控大语言模型(LLM)的调用情况。
 * 该类提供了对LLM调用的跟踪、记录和性能监控功能。
 */
@Deprecated
@Slf4j
@Component
public class DefaultLlmMonitor implements LlmMonitor {
    // 使用ThreadLocal存储当前调用的状态信息
    private static final ThreadLocal<InvocationState> CURRENT = new TransmittableThreadLocal<>();
    // 用于发布应用程序事件
    private final ApplicationEventPublisher publisher;
    // 用于记录指标
    private final MeterRegistry meterRegistry;
    // 模型提供者
    private final ModelProviders modelProviders;

    /**
     * 构造函数，注入必要的依赖
     * @param publisher 应用程序事件发布器
     * @param meterRegistry 指标注册表
     * @param modelProviders 模型提供者
     */
    public DefaultLlmMonitor(ApplicationEventPublisher publisher, MeterRegistry meterRegistry,
                             ModelProviders modelProviders) {
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
        this.modelProviders = modelProviders;
    }

    /**
     * 执行LLM调用并监控
     * @param context 调用上下文
     * @param prompt 提示信息
     * @param supplier 实际的调用逻辑
     * @param <T> 返回类型
     * @return 调用结果
     */
    @Override
    public <T> T call(LlmCallContext context, Prompt prompt, Supplier<T> supplier) {
        // 创建调用状态并设置到ThreadLocal
        InvocationState state = new InvocationState(context);
        CURRENT.set(state);
        try {
            // 执行请求
            T result = request(prompt, supplier);
            // 发布成功调用事件
            publishInvocation(state, "SUCCESS", null);
            return result;
        } catch (RuntimeException e) {
            // 发布失败调用事件
            publishInvocation(state, "FAILED", e);
            throw e;
        } finally {
            // 清理ThreadLocal和模型提供者信息
            CURRENT.remove();
            modelProviders.clearCurrentModelInfo(context.jobClawUserId());
        }
    }

    /**
     * 执行流式LLM调用并监控
     * @param context 调用上下文
     * @param prompt 提示信息
     * @param supplier 流式调用逻辑
     * @param mapper 响应映射函数
     * @param <T> 返回类型
     * @return 流式结果
     */
    @Override
    public <T> Flux<T> stream(LlmCallContext context, Prompt prompt, Supplier<Flux<ChatResponse>> supplier,
                              Function<ChatResponse, T> mapper) {
        return Flux.defer(() -> {
            // 创建调用状态并设置到ThreadLocal
            InvocationState state = new InvocationState(context);
            CURRENT.set(state);
            long start = System.nanoTime();
            StreamResponseCapture capture = new StreamResponseCapture();
            try {
                // 执行流式请求并处理各种情况
                return supplier.get()
                        .doOnNext(capture::accept)
                        .map(mapper)
                        .doOnError(e -> {
                            // 处理错误情况
                            publishStreamRequest(state, prompt, capture, "FAILED", start, e);
                            publishInvocation(state, "FAILED", e);
                        })
                        .doOnComplete(() -> {
                            // 处理完成情况
                            publishStreamRequest(state, prompt, capture, "SUCCESS", start, null);
                            publishInvocation(state, "SUCCESS", null);
                        })
                        .doOnCancel(() -> {
                            // 处理取消情况
                            publishStreamRequest(state, prompt, capture, "CANCELLED", start, null);
                            publishInvocation(state, "CANCELLED", null);
                        })
                        .doFinally(signal -> {
                            // 清理资源
                            CURRENT.remove();
                            modelProviders.clearCurrentModelInfo(context.jobClawUserId());
                        });
            } catch (RuntimeException e) {
                // 处理异常情况
                publishStreamRequest(state, prompt, capture, "FAILED", start, e);
                publishInvocation(state, "FAILED", e);
                CURRENT.remove();
                modelProviders.clearCurrentModelInfo(context.jobClawUserId());
                throw e;
            }
        });
    }

    /**
     * 直接执行LLM调用，不进行监控
     * @param prompt 提示信息
     * @param supplier 调用逻辑
     * @return 调用结果
     */
    @Override
    public ChatResponse directCall(Prompt prompt, Supplier<ChatResponse> supplier) {
        return request(prompt, supplier);
    }

    /**
     * 执行实际的LLM请求
     * @param prompt 提示信息
     * @param supplier 调用逻辑
     * @param <T> 返回类型
     * @return 调用结果
     */
    private <T> T request(Prompt prompt, Supplier<T> supplier) {
        // 获取当前调用状态
        InvocationState state = CURRENT.get();
        if (state == null) {
            // 如果没有状态，直接执行
            return supplier.get();
        }
        long start = System.nanoTime();
        try {
            // 执行请求
            T result = supplier.get();
            // 发布成功请求事件
            publishRequest(state, prompt, result instanceof ChatResponse r ? r : null, "SUCCESS", start, null);
            return result;
        } catch (RuntimeException e) {
            // 发布失败请求事件
            publishRequest(state, prompt, null, "FAILED", start, e);
            throw e;
        }
    }

    /**
     * 发布流式请求事件
     * @param state 调用状态
     * @param prompt 提示信息
     * @param capture 流式响应捕获器
     * @param outcome 结果状态
     * @param start 开始时间
     * @param error 错误信息
     */
    private void publishStreamRequest(InvocationState state, Prompt prompt, StreamResponseCapture capture,
                                      String outcome, long start, Throwable error) {
        publishRequest(state, prompt, capture.usageResponse.get(), capture.responseText.toString(), outcome, start, error);
    }

    /**
     * 发布请求事件
     * @param state 调用状态
     * @param prompt 提示信息
     * @param response 响应信息
     * @param outcome 结果状态
     * @param start 开始时间
     * @param error 错误信息
     */
    private void publishRequest(InvocationState state, Prompt prompt, ChatResponse response, String outcome, long start, Throwable error) {
        publishRequest(state, prompt, response, response == null ? null : response.getResult().getOutput().getText(),
                outcome, start, error);
    }

    /**
     * 发布请求事件（完整版本）
     * @param state 调用状态
     * @param prompt 提示信息
     * @param response 响应信息
     * @param responseText 响应文本
     * @param outcome 结果状态
     * @param start 开始时间
     * @param error 错误信息
     */
    private void publishRequest(InvocationState state, Prompt prompt, ChatResponse response, String responseText,
                                String outcome, long start, Throwable error) {
        // 计算请求持续时间
        long duration = (System.nanoTime() - start) / 1_000_000;
        // 更新状态计数器
        state.requests.incrementAndGet();
        state.duration.addAndGet(duration);
        // 获取调用上下文
        LlmCallContext c = state.context;
        // 处理提示文本
        String promptText = prompt.getInstructions().stream().map(Message::getText).reduce("", (a, b) -> a + "\n" + b);
        // 初始化token计数
        Long inputTokens = null, outputTokens = null, totalTokens = null;
        // 如果有响应和元数据，获取token使用情况
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            inputTokens = usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue();
            outputTokens = usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().longValue();
            totalTokens = usage.getTotalTokens() == null ? null : usage.getTotalTokens().longValue();
            // 更新token计数器
            state.usageKnown.set(true);
            state.inputTokens.addAndGet(inputTokens == null ? 0 : inputTokens);
            state.outputTokens.addAndGet(outputTokens == null ? 0 : outputTokens);
            state.totalTokens.addAndGet(totalTokens == null ? 0 : totalTokens);
        }
        // 获取模型信息
        var modelInfo = modelProviders.currentModelInfo(c.jobClawUserId());
        String provider = modelInfo == null ? "unknown" : modelInfo.getProvider();
        String model = modelInfo == null ? "unknown" : modelInfo.getModelName();
        String modelType = modelInfo == null || modelInfo.getType() == null ? "CHAT" : modelInfo.getType().name();
        // 计算成本
        BigDecimal cost = calculateCost(modelInfo, inputTokens, outputTokens);
        state.addCost(cost);
        // 创建请求记录
        LlmRequestRecord record = new LlmRequestRecord(UUID.randomUUID().toString(), c.invocationId(), c.jobClawUserId(),
                c.conversationId(), c.channel(), c.agent(), c.operation(), c.mode(), provider, model, modelType,
                outcome, duration, inputTokens, outputTokens, totalTokens, cost, error == null ? null : error.getMessage(),
                sample(promptText, outcome), sample(responseText, outcome),
                Instant.now());
        // 安全发布事件
        safePublish(record);
        // 记录指标
        meterRegistry.counter("jobclaw.llm.requests", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome).increment();
        Timer.builder("jobclaw.llm.request.duration").tags("agent", tag(c.agent()), "operation", tag(c.operation()),
                        "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome)
                .register(meterRegistry).record(java.time.Duration.ofMillis(duration));
        if (totalTokens != null) meterRegistry.counter("jobclaw.llm.tokens", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome).increment(totalTokens);
        if (cost != null) meterRegistry.counter("jobclaw.llm.estimated.cost", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "provider", tag(provider), "model", tag(model), "mode", tag(c.mode()), "outcome", outcome).increment(cost.doubleValue());
    }

    /**
     * 发布调用事件
     * @param state 调用状态
     * @param outcome 结果状态
     * @param error 错误信息
     */
    private void publishInvocation(InvocationState state, String outcome, Throwable error) {
        // 确保只发布一次
        if (!state.published.compareAndSet(0, 1)) return;
        // 获取调用上下文
        LlmCallContext c = state.context;
        // 创建调用记录
        safePublish(new LlmInvocationRecord(c.invocationId(), c.jobClawUserId(), c.conversationId(), c.channel(),
                c.agent(), c.operation(), c.mode(), outcome, state.duration.get(), state.requests.get(),
                state.usageKnown.get() ? state.inputTokens.get() : null,
                state.usageKnown.get() ? state.outputTokens.get() : null,
                state.usageKnown.get() ? state.totalTokens.get() : null,
                state.cost, error == null ? null : error.getMessage(), state.started));
        // 记录指标
        meterRegistry.counter("jobclaw.llm.invocations", "agent", tag(c.agent()), "operation", tag(c.operation()),
                "mode", tag(c.mode()), "outcome", outcome).increment();
    }

    /**
     * 安全发布事件
     * @param event 要发布的事件
     */
    private void safePublish(Object event) {
        try {
            publisher.publishEvent(event);
        } catch (Exception e) {
            meterRegistry.counter("jobclaw.llm.telemetry.dropped").increment();
            log.warn("Failed to publish LLM monitor event", e);
        }
    }

    /**
     * 对文本进行采样处理，去除敏感信息
     * @param text 原始文本
     * @param outcome 结果状态
     * @return 处理后的文本
     */
    private String sample(String text, String outcome) {
        if (text == null) return null;
        // 清除敏感信息
        String cleaned = text.replaceAll("(?i)Bearer\\s+\\S+", "Bearer ***")
                .replaceAll("[\\w.+-]+@[\\w.-]+", "***@***")
                .replaceAll("1[3-9]\\d{9}", "***")
                .replaceAll("\\b\\d{17}[\\dXx]\\b", "***");
        // 1% 的记录完整的提示词；否则记录前100字
        final int size = ("FAILED".equals(outcome) || Math.random() <= 0.01) ? 4000 : 100;
        return cleaned.substring(0, Math.min(cleaned.length(), size));
    }

    /**
     * 处理标签值
     * @param value 标签值
     * @return 处理后的标签值
     */
    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * 计算调用成本
     * @param info 模型信息
     * @param input 输入token数
     * @param output 输出token数
     * @return 计算出的成本
     */
    private BigDecimal calculateCost(com.git.hui.jobclaw.core.providers.ModelConfig.ModelInfo info, Long input, Long output) {
        if (info == null || (input == null && output == null)) return null;
        BigDecimal cost = BigDecimal.ZERO;
        boolean known = false;
        // 计算输入成本
        if (input != null && info.getInputPricePerMillionTokens() != null) {
            cost = cost.add(info.getInputPricePerMillionTokens().multiply(BigDecimal.valueOf(input)));
            known = true;
        }
        // 计算输出成本
        if (output != null && info.getOutputPricePerMillionTokens() != null) {
            cost = cost.add(info.getOutputPricePerMillionTokens().multiply(BigDecimal.valueOf(output)));
            known = true;
        }
        return known ? cost.divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP) : null;
    }

    /**
     * 调用状态内部类，用于跟踪单个调用的状态信息
     */
    private static class InvocationState {
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

        InvocationState(LlmCallContext context) {
            this.context = context;
        }

        /**
         * 添加成本
         * @param value 成本值
         */
        synchronized void addCost(BigDecimal value) {
            if (value != null) cost = cost == null ? value : cost.add(value);
        }
    }

    /**
     * 流式响应捕获器，用于捕获和处理流式响应
     */
    private static class StreamResponseCapture {
        final AtomicReference<ChatResponse> usageResponse = new AtomicReference<>();
        final StringBuilder responseText = new StringBuilder();

        /**
         * 接收并处理响应
         * @param response 响应对象
         */
        void accept(ChatResponse response) {
            if (response == null) return;

            log.info("Response metadata: {}, usage: {}",
                    response.getMetadata(),
                    response.getMetadata() != null ? response.getMetadata().getUsage() : null);

            // 记录使用情况
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                usageResponse.set(response);
            }
            // 记录响应文本
            if (response.getResult() != null && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                responseText.append(response.getResult().getOutput().getText());
            }
        }
    }
}
