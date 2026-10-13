package com.git.hui.jobclaw.core.configuration.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * 全局环境配置变更事件
 *
 * @date 2026/4/9
 */
@Getter
public class GlobalEnvConfigChangedEvent extends ApplicationEvent {

    /**
     * 变更的配置项 (key -> value)
     */
    private final Map<String, Object> changedConfigs;

    /**
     * 是否是完整刷新
     */
    private final boolean fullRefresh;

    public GlobalEnvConfigChangedEvent(Object source, Map<String, Object> changedConfigs, boolean fullRefresh) {
        super(source);
        this.changedConfigs = changedConfigs;
        this.fullRefresh = fullRefresh;
    }

    public GlobalEnvConfigChangedEvent(Object source, Map<String, Object> changedConfigs) {
        this(source, changedConfigs, false);
    }
}
