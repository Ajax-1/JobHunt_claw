package com.git.hui.jobclaw.core.agent.memory.archive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话归档配置属性
 * <p>
 * 控制对话历史归档的行为和策略
 *
 * @date 2026/6/5
 */
@Component
@ConfigurationProperties(prefix = "agent.archive")
public class ArchiveProperties {

    /**
     * 是否启用归档功能
     */
    private boolean enabled = true;

    /**
     * 归档文件的最大保留天数
     * 超过此天数的归档文件将被自动清理
     */
    private int maxRetentionDays = 90;

    /**
     * 单个归档文件的最大消息数量
     * 超过此数量时将拆分为多个归档文件
     */
    private int maxMessagesPerArchive = 1000;

    /**
     * 是否启用归档文件的压缩
     * 启用后将使用gzip压缩归档文件以节省存储空间
     */
    private boolean compressionEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRetentionDays() {
        return maxRetentionDays;
    }

    public void setMaxRetentionDays(int maxRetentionDays) {
        this.maxRetentionDays = maxRetentionDays;
    }

    public int getMaxMessagesPerArchive() {
        return maxMessagesPerArchive;
    }

    public void setMaxMessagesPerArchive(int maxMessagesPerArchive) {
        this.maxMessagesPerArchive = maxMessagesPerArchive;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }
}
