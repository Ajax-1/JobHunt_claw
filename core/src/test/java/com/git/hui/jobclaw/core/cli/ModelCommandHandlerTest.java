package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.preference.repository.AiUserPreferenceService;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelCommandHandlerTest {

    @Test
    void listsAvailableModelsAndCurrentDefaults() {
        ModelProviders modelProviders = mock(ModelProviders.class);
        AiUserPreferenceProperties properties = mock(AiUserPreferenceProperties.class);
        AiUserPreferenceService service = mock(AiUserPreferenceService.class);
        ModelCommandHandler handler = new ModelCommandHandler(modelProviders, properties, service);

        when(modelProviders.getModelSelection("2", ModelConfig.ModelType.TEXT)).thenReturn("deepseek#deepseek-flash");
        when(modelProviders.getModelSelection("2", ModelConfig.ModelType.VISION)).thenReturn("silicon#PaddleOCR-VL-1.5");
        when(modelProviders.listGlobalModelInfos()).thenReturn(List.of(
                model("deepseek", "deepseek-flash", ModelConfig.ModelType.TEXT),
                model("silicon", "PaddleOCR-VL-1.5", ModelConfig.ModelType.VISION),
                model("silicon", "Kwai-Kolors/Kolors", ModelConfig.ModelType.IMAGE)
        ));

        AtomicReference<String> response = new AtomicReference<>();
        boolean handled = handler.handle(new ChannelReceiveMessage(), conversation("2"), "/model", text -> {
            response.set(text);
            return true;
        });

        assertThat(handled).isTrue();
        assertThat(response.get())
                .contains("当前默认模型")
                .contains("文本：deepseek#deepseek-flash")
                .contains("视觉：silicon#PaddleOCR-VL-1.5")
                .contains("deepseek#deepseek-flash（文本） 默认文本")
                .contains("silicon#PaddleOCR-VL-1.5（视觉） 默认视觉")
                .doesNotContain("Kwai-Kolors/Kolors");
    }

    @Test
    void switchesVisionModelByModelName() {
        ModelProviders modelProviders = mock(ModelProviders.class);
        AiUserPreferenceProperties properties = mock(AiUserPreferenceProperties.class);
        AiUserPreferenceService service = mock(AiUserPreferenceService.class);
        ModelCommandHandler handler = new ModelCommandHandler(modelProviders, properties, service);

        when(modelProviders.listGlobalModelInfos()).thenReturn(List.of(
                model("deepseek", "deepseek-flash", ModelConfig.ModelType.TEXT),
                model("silicon", "PaddleOCR-VL-1.5", ModelConfig.ModelType.VISION)
        ));
        when(properties.getUserPreference("2")).thenReturn(null);
        when(properties.getUserPreference(ModelProviders.DEFAULT_PREFERENCE)).thenReturn(defaultPreference());

        AtomicReference<String> response = new AtomicReference<>();
        boolean handled = handler.handle(new ChannelReceiveMessage(), conversation("2"), "/model PaddleOCR-VL-1.5", text -> {
            response.set(text);
            return true;
        });

        assertThat(handled).isTrue();
        assertThat(response.get()).contains("已切换默认视觉模型：silicon#PaddleOCR-VL-1.5");
        ArgumentCaptor<String> preferenceJson = ArgumentCaptor.forClass(String.class);
        verify(service).saveOrUpdate(org.mockito.ArgumentMatchers.eq(2L), preferenceJson.capture());
        AiUserPreferenceProperties.UserPreferenceEntry saved = JsonUtil.toObj(
                preferenceJson.getValue(), AiUserPreferenceProperties.UserPreferenceEntry.class);
        assertThat(saved.getUserId()).isEqualTo("2");
        assertThat(saved.getModels().getText()).isEqualTo("deepseek#deepseek-flash");
        assertThat(saved.getModels().getVision()).isEqualTo("silicon#PaddleOCR-VL-1.5");
        verify(modelProviders).clearCurrentModelInfo("2");
    }

    @Test
    void asksForFullSelectionWhenModelNameIsAmbiguous() {
        ModelProviders modelProviders = mock(ModelProviders.class);
        ModelCommandHandler handler = new ModelCommandHandler(
                modelProviders, mock(AiUserPreferenceProperties.class), mock(AiUserPreferenceService.class));
        when(modelProviders.listGlobalModelInfos()).thenReturn(List.of(
                model("deepseek", "same-name", ModelConfig.ModelType.TEXT),
                model("silicon", "same-name", ModelConfig.ModelType.TEXT)
        ));

        AtomicReference<String> response = new AtomicReference<>();
        handler.handle(new ChannelReceiveMessage(), conversation("2"), "/model same-name", text -> {
            response.set(text);
            return true;
        });

        assertThat(response.get())
                .contains("模型名不唯一")
                .contains("deepseek#same-name")
                .contains("silicon#same-name");
    }

    @Test
    void rejectsUnboundUser() {
        ModelProviders modelProviders = mock(ModelProviders.class);
        AiUserPreferenceService service = mock(AiUserPreferenceService.class);
        ModelCommandHandler handler = new ModelCommandHandler(
                modelProviders, mock(AiUserPreferenceProperties.class), service);

        AtomicReference<String> response = new AtomicReference<>();
        boolean handled = handler.handle(new ChannelReceiveMessage(), conversation("open-id"), "/model", text -> {
            response.set(text);
            return true;
        });

        assertThat(handled).isTrue();
        assertThat(response.get()).contains("仅绑定JobClaw账号");
        verify(modelProviders, never()).listGlobalModelInfos();
        verify(service, never()).saveOrUpdate(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void supportsModelCommandWithOptionalArgument() {
        ModelCommandHandler handler = new ModelCommandHandler(
                mock(ModelProviders.class), mock(AiUserPreferenceProperties.class), mock(AiUserPreferenceService.class));

        assertThat(handler.supports("/model")).isTrue();
        assertThat(handler.supports("/model deepseek-flash")).isTrue();
        assertThat(handler.supports("/models")).isFalse();
        assertThat(handler.getIntentType()).isEqualTo(PresetAgentIntro.MODEL);
    }

    private static UserConversationInfo conversation(String userId) {
        return new UserConversationInfo(userId, "test", "conversation-1", false);
    }

    private static ModelConfig.ModelInfo model(String provider, String modelName, ModelConfig.ModelType type) {
        return ModelConfig.ModelInfo.builder()
                .provider(provider)
                .modelName(modelName)
                .type(type)
                .build();
    }

    private static AiUserPreferenceProperties.UserPreferenceEntry defaultPreference() {
        AiUserPreferenceProperties.UserPreferenceEntry entry = new AiUserPreferenceProperties.UserPreferenceEntry();
        entry.setUserId(ModelProviders.DEFAULT_PREFERENCE);
        AiUserPreferenceProperties.UserModelPreference models = new AiUserPreferenceProperties.UserModelPreference();
        models.setText("deepseek#deepseek-flash");
        models.setVision("silicon#PaddleOCR-VL-1.5");
        entry.setModels(models);
        return entry;
    }
}
