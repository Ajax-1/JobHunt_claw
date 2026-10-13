package com.git.hui.jobclaw.core.agent.models;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class LlmRspCellTest {
    @Test
    void keepsWhitespaceWithEmptyReasoningMetadata() {
        var message = AssistantMessage.builder().content("\n\n").properties(Map.of("reasoningContent", "")).build();
        var cell = LlmRspCell.of(new ChatResponse(List.of(new Generation(message))));
        assertThat(cell.content()).isEqualTo("\n\n");
    }
}
