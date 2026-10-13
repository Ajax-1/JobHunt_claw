package com.git.hui.jobclaw.core.agent.extraction;

/** AI-GENERATED AIDEV-NOTE: 编号由输入端分配，重试保持不变。 */
public record SourceRecord(String sourceId, String content) {
    public SourceRecord {
        if (sourceId == null || sourceId.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("来源编号和内容不能为空");
        }
    }
}
