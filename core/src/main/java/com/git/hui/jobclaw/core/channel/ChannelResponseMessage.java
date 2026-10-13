package com.git.hui.jobclaw.core.channel;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 响应消息
 * @date 2026/4/8
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResponseMessage {
    private String jobClawUserId;
    private String toUserId;
    private ResponseMessageType type;
    private String content;
    private Flux<LlmRspCell> streamContents;
    private Map<String, Object> passThrough;


    public enum ResponseMessageType {
        TEXT("text"),
        MARKDOWN("markdown"),
        CARD("card"),
        ;

        private String type;

        ResponseMessageType(String type) {
            this.type = type;
        }
    }
}
