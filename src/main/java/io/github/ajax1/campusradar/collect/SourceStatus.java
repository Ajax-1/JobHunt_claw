package io.github.ajax1.campusradar.collect;

import java.time.Instant;

/**
 * 每个数据源的现状：在招岗位数 + 最近一次采集结果，给 Agent 判断数据新鲜度用
 */
public record SourceStatus(String source, String company, long openJobs, String lastRunStatus,
                           Instant lastRunAt, Integer lastFetched, String lastMessage) {
}
