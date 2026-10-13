package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.tasks.TaskManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// AI-GENERATED AIDEV-NOTE: 防止用户画像覆盖岗位检索规则，造成无工具的空泛回复。
class UserPreferencePromptTest {
    @Test
    void preservesBusinessInstructionsAlongsideIdentity() {
        var identity = mock(IIdentityAgent.class);
        when(identity.buildSystemPrompt("1")).thenReturn("用户希望在北京求职");
        var caller = new UserPreferenceBasedLlmCaller(mock(ModelProviders.class), identity,
                mock(ChatMemory.class), mock(TaskManager.class), List.of(), "必须调用职位库工具，不能编造岗位");
        var prompt = caller.buildSoulPrompt("1", ChannelReceiveMessage.builder().message("推荐研发岗位").build());
        assertThat(prompt.getSystemMessage().getText()).contains("北京", "必须调用职位库工具", "不能编造岗位");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("推荐研发岗位");
    }
}
