package com.git.hui.jobclaw.channels.websocket;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelMsgAdapter;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.FileSystemResource;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// AI-GENERATED AIDEV-NOTE: 覆盖首次画像主动回复及流式失败，避免消息静默丢失。
class WebSocketChannelTest {
    private final ChannelRegistry registry = new ChannelRegistry();
    private final WsSessionManager sessions = mock(WsSessionManager.class);
    private final WebSocketChannel channel = new WebSocketChannel(new FileSystemResource("/tmp/jobclaw-ws-test"),
            registry, mock(ChannelEventPublisher.class), mock(ConfigurationManager.class), sessions,
            new WebSocketProperties());

    @Test
    void firstMessageRegistersProactiveResponseAdapter() {
        var message = new WsMessage();
        message.setMsgId("first");
        message.setMessage("你好");
        channel.processMessage(ChannelMsgAdapter.MsgWrapper.<WsMessage>builder()
                .jobClawUserId("1").msg(message).build());
        var adapter = registry.getChannelRspBuilderAdapter("1", channel.name());
        assertThat(adapter).isNotNull();
        var response = adapter.apply(Flux.just(new LlmRspCell(null, "欢迎", null)));
        assertThat(response.getJobClawUserId()).isEqualTo("1");
        channel.responseToUser(response);
        var messages = ArgumentCaptor.forClass(WsResponseMessage.class);
        verify(sessions, times(2)).sendToUser(eq("1"), anyString(), messages.capture());
        assertThat(messages.getAllValues()).extracting(WsResponseMessage::getType)
                .containsExactly("stream", "stream_done");
        assertThat(messages.getAllValues().getFirst().getContent()).isEqualTo("欢迎");
    }

    @Test
    void failedStreamReturnsVisibleErrorWithoutInternalDetails() {
        var response = channel.buildHeartBeatCallback("1").apply(Flux.error(new IllegalStateException("private-key")));
        channel.responseToUser(response);
        var message = ArgumentCaptor.forClass(WsResponseMessage.class);
        verify(sessions).sendToUser(eq("1"), eq("/queue/rsp/error"), message.capture());
        assertThat(message.getValue().getType()).isEqualTo("error");
        assertThat(message.getValue().getContent()).contains("稍后重试").doesNotContain("private-key");
    }
}
