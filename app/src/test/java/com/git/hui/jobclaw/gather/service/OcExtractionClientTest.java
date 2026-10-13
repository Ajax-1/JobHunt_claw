package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.constants.gather.GatherModelTypeEnum;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.gather.model.ModelSelectReq;
import com.git.hui.jobclaw.gather.service.ai.OcAiModelContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.data.util.Pair;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** AI-GENERATED AIDEV-NOTE: 每批输出预算不能覆盖 DeepSeek 采集专用的关闭思考选项。 */
class OcExtractionClientTest {
    @Test
    void deepseekExtractionPreservesReasoningModeAndPerBatchBudget() {
        var model = mock(ChatModel.class);
        when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("deepseek-flash").build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{}")))));
        var context = spy(new OcAiModelContext(List.of(), mock(ModelProviders.class)));
        doReturn(Pair.of(model, "deepseek-flash")).when(context).model(any());
        context.extractionCaller(ModelSelectReq.of("deepseek#deepseek-flash", GatherModelTypeEnum.CHAT_MODEL))
                .apply(new Prompt("提取", ChatOptions.builder().maxTokens(8192).build()));
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        var options = assertInstanceOf(OpenAiChatOptions.class, captor.getValue().getOptions());
        assertEquals("none", options.getReasoningEffort());
        assertEquals(8192, options.getMaxTokens());
    }
}
