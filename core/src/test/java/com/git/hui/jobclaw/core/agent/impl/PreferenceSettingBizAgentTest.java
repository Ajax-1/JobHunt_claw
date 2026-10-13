package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.preference.repository.AiUserPreferenceService;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// AI-GENERATED AIDEV-NOTE: 新账号查询和修改偏好不能空指针或污染全局配置。
class PreferenceSettingBizAgentTest {
    @Test void newUserUsesDefaultModelWithoutGlobalSecretsOrMutation() {
        var props = mock(AiUserPreferenceProperties.class);
        var defaults = new AiUserPreferenceProperties.UserPreferenceEntry();
        var models = new AiUserPreferenceProperties.UserModelPreference();
        models.setText("deepseek#deepseek-flash");
        defaults.setModels(models);
        var provider = new AiUserPreferenceProperties.ProviderConfig();
        provider.setApiKey("test-secret");
        defaults.setProviders(Map.of("deepseek", provider));
        when(props.getUserPreference("total")).thenReturn(defaults);
        var service = mock(AiUserPreferenceService.class);
        var agent = new PreferenceSettingBizAgent(null, List.of(), props, null, null, service);
        agent.init();
        var context = new ToolContext(Map.of("user", new UserConversationInfo("1", "ws", "1", false)));
        var result = agent.showMyPreference(context);
        assertThat(result.getUserId()).isEqualTo("1");
        assertThat(result.getModels().getText()).isEqualTo("deepseek#deepseek-flash");
        assertThat(result.getProviders()).isEmpty();
        assertThat(agent.updateActiveModel("demo", "model", ModelConfig.ModelType.TEXT, context)).isEqualTo("设置成功");
        assertThat(models.getText()).isEqualTo("deepseek#deepseek-flash");
        verify(service).saveOrUpdate(eq(1L), contains("demo#model"));
    }
}
