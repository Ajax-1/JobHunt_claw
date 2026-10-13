package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.gather.dao.entity.GatherSourceEntity;
import com.git.hui.jobclaw.gather.dao.repository.GatherTaskRepository;
import com.git.hui.jobclaw.gather.model.GatherTaskSaveBo;
import com.git.hui.jobclaw.gather.service.helper.LocalStorageHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** AI-GENERATED AIDEV-NOTE: 作业台省略模型也可保存，输入分类前不固定文本模型。 */
class GatherTaskServiceModelTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "deepseek#deepseek-flash")
    void unclassifiedTaskAcceptsOmittedModel(String requestedModel) throws Exception {
        var tasks = mock(GatherTaskRepository.class);
        var sources = mock(GatherSourceService.class);
        var models = mock(ModelProviders.class);
        var service = new GatherTaskService(tasks, mock(LocalStorageHelper.class), sources, models);
        when(sources.resolveSource(eq(0), anyString(), eq("招聘原文"), eq("agent")))
                .thenReturn(new GatherSourceEntity().setId(1L));

        var task = service.directAddTask(new GatherTaskSaveBo(null, requestedModel, "招聘原文", null));

        String expected = requestedModel == null ? "" : requestedModel;
        assertEquals(expected, task.getModel());
        assertEquals(0, task.getType());
        verify(sources).resolveSource(0, expected, "招聘原文", "agent");
        verify(tasks).saveAndFlush(task);
        verifyNoInteractions(models);
    }
}
