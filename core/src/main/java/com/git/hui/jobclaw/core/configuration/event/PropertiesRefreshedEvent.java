package com.git.hui.jobclaw.core.configuration.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 *
 * @date 2026/4/18
 */
public class PropertiesRefreshedEvent extends ApplicationEvent {

    @Getter
    private Class propertiesClz;

    public PropertiesRefreshedEvent(Object source, Class properties) {
        super(source);
        this.propertiesClz = properties;
    }
}
