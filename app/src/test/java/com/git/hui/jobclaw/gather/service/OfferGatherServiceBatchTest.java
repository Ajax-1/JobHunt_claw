package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.configs.service.CommonDictService;
import com.git.hui.jobclaw.core.agent.extraction.ExtractionReport;
import com.git.hui.jobclaw.core.agent.extraction.SourceRecord;
import com.git.hui.jobclaw.gather.model.GatherFileBo;
import com.git.hui.jobclaw.gather.model.GatherOcDraftBo;
import com.git.hui.jobclaw.oc.service.GatherService;
import com.git.hui.jobclaw.web.hook.extend.GlobalExceptionHandler;
import com.git.hui.jobclaw.web.model.req.GatherReq;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** AI-GENERATED AIDEV-NOTE: 采集未完成时禁止写草稿，并验证接口可见失败记录。 */
class OfferGatherServiceBatchTest {
    @Test
    @SuppressWarnings("unchecked")
    void failedCsvBatchDoesNotSavePartialDrafts() throws Exception {
        var ai = mock(GatherAiAgent.class);
        var drafts = mock(GatherService.class);
        var service = new OfferGatherService(ai, drafts, mock(GatherTaskService.class), mock(CommonDictService.class));
        var report = new ExtractionReport<GatherOcDraftBo>(List.of(
                new ExtractionReport.RecordResult<>("row-1", List.of(), null),
                new ExtractionReport.RecordResult<>("row-2", List.of(), "输出截断")), 3, 2);
        when(ai.gatherRecords(anyString(), anyList())).thenReturn(report);
        var file = new GatherFileBo("公司,岗位\nA,Java\nB,测试\n".getBytes(StandardCharsets.UTF_8), "text/csv", "jobs.csv");
        var error = assertThrows(ExtractionReport.IncompleteExtractionException.class,
                () -> service.gatherFileInfo(new GatherReq("", 5, "deepseek#deepseek-flash"), file));
        verifyNoInteractions(drafts);
        ArgumentCaptor<List<SourceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(ai).gatherRecords(eq("deepseek#deepseek-flash"), captor.capture());
        assertEquals(2, captor.getValue().size());
        var response = new GlobalExceptionHandler().handleIncompleteExtraction(error);
        assertEquals(422, response.getCode());
        assertSame(report, response.getData());
        assertTrue(response.getMsg().contains("row-2"));
    }
}
