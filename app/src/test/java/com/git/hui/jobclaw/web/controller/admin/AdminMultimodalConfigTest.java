package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.web.model.res.AdminLlmProviderVo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// AI-GENERATED AIDEV-NOTE: TEXT 多模态能力须经保存保留，并可选为默认视觉模型。
class AdminMultimodalConfigTest {
    @Test void savingTextModelPreservesMultimodalFlag() {
        var controller = new AdminGlobalEnvConfigController();
        var model = new AdminLlmProviderVo.ModelConfigVo();
        model.setName("deepseek-flash"); model.setType("TEXT"); model.setMultimodal(true);
        var config = new AdminLlmProviderVo.ProviderConfigVo(); config.setModels(List.of(model));
        Map<String, Object> flattened = ReflectionTestUtils.invokeMethod(controller, "flattenProvider", "deepseek", config, "test-key");
        assertThat(flattened).containsEntry("agent.ai.providers.deepseek.models[0].multimodal", true);
    }

    @Test void visionAcceptsMultimodalTextAndRejectsTextOnly() {
        var controller = new AdminGlobalEnvConfigController();
        var models = mock(ModelProviders.class);
        ReflectionTestUtils.setField(controller, "modelProviders", models);
        when(models.getGlobalModelInfo("deepseek#deepseek-flash")).thenReturn(
                ModelConfig.ModelInfo.builder().type(ModelConfig.ModelType.TEXT).multimodal(true).build());
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(controller, "validateDefaultModel",
                "deepseek#deepseek-flash", ModelConfig.ModelType.VISION, "视觉")).doesNotThrowAnyException();
        when(models.getGlobalModelInfo("plain#text")).thenReturn(
                ModelConfig.ModelInfo.builder().type(ModelConfig.ModelType.TEXT).multimodal(false).build());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(controller, "validateDefaultModel",
                "plain#text", ModelConfig.ModelType.VISION, "视觉")).isInstanceOf(IllegalArgumentException.class);
    }
}
