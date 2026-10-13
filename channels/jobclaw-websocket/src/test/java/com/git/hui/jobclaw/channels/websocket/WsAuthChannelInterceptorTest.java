package com.git.hui.jobclaw.channels.websocket;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

// AI-GENERATED AIDEV-NOTE: 覆盖身份验证与用户队列边界，防止跨用户订阅和消息伪造。
class WsAuthChannelInterceptorTest {
    private final WsAuthChannelInterceptor interceptor = new WsAuthChannelInterceptor("qa-test-secret", "jobclaw");

    @Test
    void acceptsValidTokenAndRejectsExpiredOrMissingToken() {
        var valid = StompHeaderAccessor.create(StompCommand.CONNECT);
        valid.setLeaveMutable(true);
        valid.setNativeHeader("X-OC-TOKEN", JWT.create().withIssuer("jobclaw").withClaim("uid", "1")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256("qa-test-secret")));
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], valid.getMessageHeaders()), null);
        assertThat(valid.getUser().getName()).isEqualTo("1");
        var invalid = StompHeaderAccessor.create(StompCommand.CONNECT);
        invalid.setLeaveMutable(true);
        assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], invalid.getMessageHeaders()), null))
                .isInstanceOf(IllegalArgumentException.class);
        invalid.setNativeHeader("X-OC-TOKEN", JWT.create().withIssuer("jobclaw").withClaim("uid", "1")
                .withExpiresAt(Instant.now().minusSeconds(10)).sign(Algorithm.HMAC256("qa-test-secret")));
        assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], invalid.getMessageHeaders()), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAllowsOwnUserQueuesAndApplicationChatDestination() {
        for (String destination : new String[]{"/user/queue/rsp", "/user/queue/rsp/stream", "/user/queue/rsp/done", "/user/queue/rsp/error"}) {
            assertThatCode(() -> send(StompCommand.SUBSCRIBE, destination)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/user/2/queue/rsp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/queue/rsp-user-other")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> send(StompCommand.SEND, "/user/2/queue/rsp")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> send(StompCommand.SEND, "/app/chat.send")).doesNotThrowAnyException();
    }

    private void send(StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(new WsAuthChannelInterceptor.WsPrincipal("1", "测试"));
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }
}
