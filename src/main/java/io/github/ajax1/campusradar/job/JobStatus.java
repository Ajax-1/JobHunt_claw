package io.github.ajax1.campusradar.job;

public enum JobStatus {
    /**
     * 最近一次采集仍能在官网看到
     */
    OPEN,
    /**
     * 官网已下线，保留记录供投递台账引用
     */
    CLOSED
}
