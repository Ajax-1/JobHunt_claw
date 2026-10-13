package com.git.hui.jobclaw.core.agent.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** AI-GENERATED AIDEV-NOTE: 用可控响应验证重试边界、来源覆盖和失败传播。 */
class StructuredBatchExtractorTest {
    record Job(String company, String position) { }
    private final ObjectMapper mapper = new ObjectMapper();

    private StructuredBatchExtractor<Job> extractor(int maxRequests) {
        return new StructuredBatchExtractor<>(Job.class, j -> j.company() != null && !j.company().isBlank(),
                new StructuredBatchExtractor.Limits(8192, 8, 12000, maxRequests));
    }

    private List<SourceRecord> sources(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new SourceRecord("r" + i, "公司" + i)).toList();
    }

    private List<SourceRecord> input(Prompt prompt) {
        try {
            return mapper.readValue(prompt.getUserMessage().getText(),
                    mapper.getTypeFactory().constructCollectionType(List.class, SourceRecord.class));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private ChatResponse response(String text, String reason) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(reason).build())));
    }

    private ChatResponse success(List<SourceRecord> sources) {
        try {
            var rows = sources.stream().map(s -> Map.of("sourceId", s.sourceId(), "status", "ok", "items",
                    List.of(new Job(s.content(), "Java工程师")))).toList();
            return response(mapper.writeValueAsString(Map.of("records", rows)), "stop");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void truncatedParentIsDiscardedAndChildrenPreserveEverySourceExactlyOnce() {
        List<Integer> batchSizes = new ArrayList<>();
        var report = extractor(20).extract(sources(8), "提取", prompt -> {
            var batch = input(prompt);
            batchSizes.add(batch.size());
            assertEquals(2, prompt.getInstructions().size());
            assertEquals(8192, prompt.getOptions().getMaxTokens());
            return batch.size() > 2 ? response("{\"records\":[", "length") : success(batch);
        });
        assertEquals(List.of(8, 4, 2, 2, 4, 2, 2), batchSizes);
        assertEquals(3, report.truncatedResponses());
        assertEquals(8, report.requireComplete().size());
        assertEquals(sources(8).stream().map(SourceRecord::sourceId).toList(),
                report.records().stream().map(ExtractionReport.RecordResult::sourceId).toList());
    }

    @Test
    void validJsonWithLengthFinishReasonStillRetries() {
        AtomicInteger calls = new AtomicInteger();
        var report = extractor(10).extract(sources(2), "", prompt -> {
            var result = success(input(prompt));
            return calls.getAndIncrement() == 0 ? response(result.getResult().getOutput().getText(), "length") : result;
        });
        assertEquals(3, report.requests());
        assertEquals(2, report.requireComplete().size());
    }

    @Test
    void missingSourceTriggersRetryEvenWithNormalFinish() {
        var report = extractor(10).extract(sources(2), "", prompt -> success(input(prompt).subList(0, 1)));
        assertEquals(3, report.requests());
        assertEquals(2, report.requireComplete().size());
    }

    @Test
    void repeatedSourceIsNotAccepted() {
        var report = extractor(10).extract(sources(1), "", prompt -> success(List.of(input(prompt).getFirst(), input(prompt).getFirst())));
        assertFalse(report.complete());
        assertEquals(2, report.requests());
        assertThrows(ExtractionReport.IncompleteExtractionException.class, report::requireComplete);
    }

    @Test
    void malformedSingleRecordIsBoundedAndExposesPartialReport() {
        var report = extractor(10).extract(sources(2), "", prompt ->
                input(prompt).getFirst().sourceId().equals("r1") ? response("{", "stop") : success(input(prompt)));
        assertEquals(1, report.records().stream().filter(ExtractionReport.RecordResult::succeeded).count());
        var error = assertThrows(ExtractionReport.IncompleteExtractionException.class, report::requireComplete);
        assertSame(report, error.getReport());
        assertTrue(error.getMessage().contains("r1"));
    }

    @Test
    void budgetExhaustionMarksRemainingSourcesFailed() {
        var report = extractor(1).extract(sources(8), "", prompt -> response("{", "length"));
        assertEquals(1, report.requests());
        assertEquals(8, report.records().size());
        assertFalse(report.complete());
    }

    @Test
    void transportFailureDoesNotExplodeIntoRetries() {
        var report = extractor(128).extract(sources(20), "", prompt -> { throw new IllegalStateException("secret"); });
        assertEquals(1, report.requests());
        assertEquals(20, report.records().size());
        assertFalse(report.toString().contains("secret"));
    }

    @Test
    void missingRequiredFieldAndTrailingGarbageAreRejected() {
        for (String content : List.of(
                "{\"records\":[{\"sourceId\":\"r1\",\"status\":\"ok\",\"items\":[{\"company\":\"A\"}]}]}",
                "{\"records\":[{\"sourceId\":\"r1\",\"status\":\"empty\",\"items\":[]}]} trailing")) {
            assertFalse(extractor(4).extract(sources(1), "", prompt -> response(content, "stop")).complete());
        }
    }

    @Test
    void explicitEmptySourceIsAcknowledgedButRefusalIsNot() {
        String empty = "{\"records\":[{\"sourceId\":\"r1\",\"status\":\"empty\",\"items\":[]}]}";
        assertTrue(extractor(4).extract(sources(1), "", p -> response(empty, "stop")).requireComplete().isEmpty());
        assertFalse(extractor(4).extract(sources(1), "", p -> response(empty, "content_filter")).complete());
    }

    @Test
    void rejectsDuplicateInputIdsBeforeCallingModel() {
        var source = sources(1).getFirst();
        assertThrows(IllegalArgumentException.class, () -> extractor(4).extract(List.of(source, source), "",
                p -> { throw new AssertionError("不应调用"); }));
    }

    @Test
    void inputBudgetSeparatesLongRecordsWithoutSplittingTheirContent() {
        var large = new SourceRecord("large", "岗位".repeat(7000));
        var report = extractor(8).extract(List.of(large, sources(1).getFirst()), "", p -> success(input(p)));
        assertEquals(2, report.requests());
        assertEquals(large.content(), report.requireComplete().getFirst().company());
    }
}
