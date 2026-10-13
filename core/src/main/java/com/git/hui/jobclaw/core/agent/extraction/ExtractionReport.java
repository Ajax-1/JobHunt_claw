package com.git.hui.jobclaw.core.agent.extraction;

import java.util.List;

/** AI-GENERATED AIDEV-NOTE: 保留逐条结果，禁止部分成功伪装为完整列表。 */
public record ExtractionReport<T>(List<RecordResult<T>> records, int requests, int truncatedResponses) {
    public ExtractionReport {
        records = List.copyOf(records);
    }

    public record RecordResult<T>(String sourceId, List<T> items, String failure) {
        public RecordResult {
            items = List.copyOf(items);
        }

        public boolean succeeded() {
            return failure == null;
        }
    }

    public boolean complete() {
        return records.stream().allMatch(RecordResult::succeeded);
    }

    public List<T> requireComplete() {
        if (!complete()) {
            throw new IncompleteExtractionException(this);
        }
        return records.stream().flatMap(record -> record.items().stream()).toList();
    }

    public static final class IncompleteExtractionException extends RuntimeException {
        private final ExtractionReport<?> report;

        public IncompleteExtractionException(ExtractionReport<?> report) {
            super("提取未完成，失败记录：" + report.records().stream()
                    .filter(record -> !record.succeeded())
                    .map(record -> record.sourceId() + "(" + record.failure() + ")").toList());
            this.report = report;
        }

        public ExtractionReport<?> getReport() {
            return report;
        }
    }
}
