package com.git.hui.jobclaw.core.monitor;

import com.git.hui.jobclaw.core.monitor.del.DefaultLlmMonitor;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DefaultLlmMonitorTest {

    @Test
    void recordsUsageBeforeMappingStreamResponse() {
        List<Object> events = new ArrayList<>();
        DefaultLlmMonitor monitor = monitor(events);
        Prompt prompt = new Prompt(new UserMessage("hello"));
        LlmCallContext context = new LlmCallContext("inv-1", "user-1", "conversation-1",
                "test", "agent-1", "agent_chat", "STREAM");

        List<String> result = monitor.stream(context, prompt,
                        () -> Flux.just(response("hel", null), response("lo", new DefaultUsage(12, 5, 17))),
                        response -> response.getResult().getOutput().getText())
                .collectList()
                .block();

        assertThat(result).containsExactly("hel", "lo");
        LlmRequestRecord request = event(events, LlmRequestRecord.class);
        assertThat(request.inputTokens()).isEqualTo(12);
        assertThat(request.outputTokens()).isEqualTo(5);
        assertThat(request.totalTokens()).isEqualTo(17);

        LlmInvocationRecord invocation = event(events, LlmInvocationRecord.class);
        assertThat(invocation.requestCount()).isEqualTo(1);
        assertThat(invocation.inputTokens()).isEqualTo(12);
        assertThat(invocation.outputTokens()).isEqualTo(5);
        assertThat(invocation.totalTokens()).isEqualTo(17);
    }

    @Test
    void recordsRequestWhenStreamFailsAsynchronously() {
        List<Object> events = new ArrayList<>();
        DefaultLlmMonitor monitor = monitor(events);
        Prompt prompt = new Prompt(new UserMessage("hello"));
        LlmCallContext context = new LlmCallContext("inv-2", "user-1", "conversation-1",
                "test", "agent-1", "agent_chat", "STREAM");

        assertThatThrownBy(() -> monitor.stream(context, prompt,
                        () -> Flux.error(new IllegalStateException("stream failed")),
                        response -> response.getResult().getOutput().getText())
                .collectList()
                .block())
                .hasMessage("stream failed");

        LlmRequestRecord request = event(events, LlmRequestRecord.class);
        assertThat(request.outcome()).isEqualTo("FAILED");
        assertThat(request.errorMessage()).isEqualTo("stream failed");
    }

    private static ChatResponse response(String text, DefaultUsage usage) {
        ChatResponseMetadata metadata = usage == null
                ? ChatResponseMetadata.builder().build()
                : ChatResponseMetadata.builder().usage(usage).build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }

    private static DefaultLlmMonitor monitor(List<Object> events) {
        return new DefaultLlmMonitor(events::add, new SimpleMeterRegistry(), mock(ModelProviders.class));
    }

    private static <T> T event(List<Object> events, Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
