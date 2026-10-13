package com.git.hui.jobclaw.core.configuration;

import com.git.hui.jobclaw.core.configuration.event.GlobalEnvConfigChangedEvent;
import com.git.hui.jobclaw.core.configuration.event.PropertiesRefreshedEvent;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局环境配置处理器
 * 从数据库(global_env_config表)加载配置,并以最高优先级注入到Spring Environment
 * 支持配置的动态刷新
 *
 */
@Slf4j
@Order(1)
@Component
public class ConfigurationManager implements CommandLineRunner {

    private final ApplicationEventPublisher eventPublisher;

    private final EnvConfigRepository envConfigRepository;

    private final ConfigurableEnvironment environment;
    private DynamicConfigBinder dynamicBinder;

    /**
     * 配置变更的回调任务
     */
    private Map<Class, Runnable> refreshCallback = new ConcurrentHashMap<>();

    public ConfigurationManager(ConfigurableEnvironment environment, ApplicationEventPublisher eventPublisher, EnvConfigRepository envConfigRepository) {
        this.environment = environment;
        this.eventPublisher = eventPublisher;
        this.envConfigRepository = envConfigRepository;
    }

    @Transactional
    public void updateProperties(Map<String, Object> keyValues) {
        // 为了避免刷新配置之后，立马取配置拿不到最新的情况，我们先主动写入缓存中
        // 与之搭配的，应该使用下面的 getProperty 获取配置
        cachedConfig.putAll(keyValues);
        // 持久化变更的配置
        keyValues.forEach((k, v) -> {
            if (v instanceof String) {
                envConfigRepository.saveOrUpdateConfig(k, (String) v, "string", "");
            } else if (v instanceof Integer) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "int", "");
            } else if (v instanceof Boolean) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "boolean", "");
            } else if (v instanceof Long) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "long", "");
            } else if (v instanceof Double) {
                envConfigRepository.saveOrUpdateConfig(k, String.valueOf(v), "double", "");
            } else {
                envConfigRepository.saveOrUpdateConfig(k, JsonUtil.toStr(v), "json", "");
            }
        });

        eventPublisher.publishEvent(new GlobalEnvConfigChangedEvent(this, keyValues));
    }

    public String getProperty(String key) {
        if (cachedConfig.containsKey(key)) {
            return String.valueOf(cachedConfig.get(key));
        }

        try {
            return environment.getProperty(key);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * 监听配置变更事件
     */
    @Async
    @EventListener
    public void onConfigChanged(GlobalEnvConfigChangedEvent event) {
        log.info("[GlobalEnvConfig] Received config change event, changed keys: {}",
                event.getChangedConfigs().keySet());
        this.autoRefreshConfig(true);

    }

    /**
     * 定时刷新配置(每5分钟执行一次)
     * AIDEV-NOTE: 可根据实际需求调整cron表达式
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void refreshConfig() {
        autoRefreshConfig(false);
    }

    /**
     * 注册配置变更的回调事件
     *
     * @param bean  监听的配置对象
     * @param run
     */
    public void registerCallback(Object bean, Runnable run) {
        refreshCallback.put(bean.getClass(), run);
    }


    // 缓存已加载的配置,用于动态刷新时对比
    private final Map<String, Object> cachedConfig = new ConcurrentHashMap<>();
    private static final String PROPERTY_SOURCE_NAME = "GlobalEnvConfig";


    @Override
    public void run(String... args) throws Exception {
        try {
            Map<String, Object> properties = loadConfigFromDatabase();

            if (properties.isEmpty()) {
                log.info("[GlobalEnvConfig] No configuration found in database");
                if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
                    environment.getPropertySources().replace(PROPERTY_SOURCE_NAME,
                            new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
                } else {
                    environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
                }
                return;
            }

            // 缓存配置
            cachedConfig.clear();
            cachedConfig.putAll(properties);

            // 添加到Environment,使用最高优先级
            if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().replace(PROPERTY_SOURCE_NAME,
                        new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
                log.info("[GlobalEnvConfig] Replaced existing property source with {} configurations",
                        properties.size());
            } else {
                environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
                log.info("[GlobalEnvConfig] Added property source with {} configurations", properties.size());
            }

            // 打印加载的配置(脱敏)
            if (log.isInfoEnabled()) {
                properties.forEach((key, value) -> log.info("[GlobalEnvConfig] Loaded: {} = {}", key, value));
            }
            // 从数据库中加载完配置，触发一次刷新
            this.autoRefreshConfig(true);
        } catch (Exception e) {
            log.error("[GlobalEnvConfig] Failed to load configuration from database", e);
        }
    }

    public void autoRefreshConfig(boolean force) {
        try {
            Map<String, Object> newConfig = loadConfigFromDatabase();

            // 检测配置变更
            boolean hasChanged = detectConfigChanges(newConfig);

            if (force || hasChanged) {
                log.info("[GlobalEnvConfig] Scheduled configuration check found changes, refreshing...");
                applyConfigToEnvironment(newConfig);
                cachedConfig.clear();
                cachedConfig.putAll(newConfig);
                log.info("[GlobalEnvConfig] Configuration refreshed successfully");
            }
        } catch (Exception e) {
            log.error("[GlobalEnvConfig] Failed to refresh configuration", e);
        }
    }

    /**
     * 从数据库加载配置
     *
     * @return 配置Map
     */
    private Map<String, Object> loadConfigFromDatabase() {
        var configs = envConfigRepository.findByEnabledOrderByCreateTimeAsc(1);

        Map<String, Object> properties = new LinkedHashMap<>();
        for (var temp : configs) {
            var config = ((EnvConfigRepository.EnvConfig) temp);
            String key = config.getConfigKey();
            String value = config.getConfigValue();

            if (key != null && !key.trim().isEmpty() && value != null) {
                properties.put(key.trim(), convertValue(value, config.getConfigType()));
            }
        }
        return properties;
    }

    /**
     * 检测配置变更
     *
     * @param newConfig 新配置
     * @return 是否有变更
     */
    private boolean detectConfigChanges(Map<String, Object> newConfig) {
        // 检查新增或修改的配置
        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = cachedConfig.get(key);

            if (oldValue == null || !oldValue.equals(newValue)) {
                log.info("[GlobalEnvConfig] Config changed: {} = {} -> {}",
                        key,
                        oldValue,
                        newValue);
                return true;
            }
        }

        // 检查删除的配置
        for (String key : cachedConfig.keySet()) {
            if (!newConfig.containsKey(key)) {
                log.info("[GlobalEnvConfig] Config removed: {}", key);
                return true;
            }
        }

        return false;
    }

    /**
     * 将新配置应用到Environment
     *
     * @param newConfig 新配置
     */
    private void applyConfigToEnvironment(Map<String, Object> newConfig) {
        environment.getPropertySources().replace(PROPERTY_SOURCE_NAME,
                new MapPropertySource(PROPERTY_SOURCE_NAME, newConfig));

        var applicationContext = SpringUtil.getContext();
        applicationContext.getBeansWithAnnotation(ConfigurationProperties.class).values().forEach(bean -> {
            var clz = bean.getClass();
            Bindable<?> target = Bindable.ofInstance(bean).withAnnotations(AnnotationUtils.findAnnotation(clz,
                    ConfigurationProperties.class));
            if (this.dynamicBinder == null) {
                this.dynamicBinder = new DynamicConfigBinder(applicationContext, environment.getPropertySources());
            }
            this.dynamicBinder.bind(target);
            eventPublisher.publishEvent(new PropertiesRefreshedEvent(this, clz));
            var run = this.refreshCallback.get(clz);
            if (run != null) {
                run.run();
            }
        });
    }

    /**
     * 转换配置值类型
     *
     * @param value 字符串值
     * @param type  类型
     * @return 转换后的值
     */
    private Object convertValue(String value, String type) {
        if (type == null || type.trim().isEmpty()) {
            return value;
        }

        switch (type.toLowerCase().trim()) {
            case "int":
            case "integer":
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    log.warn("[GlobalEnvConfig] Failed to parse integer value: {}", value);
                    return value;
                }
            case "long":
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    log.warn("[GlobalEnvConfig] Failed to parse long value: {}", value);
                    return value;
                }
            case "boolean":
                return Boolean.parseBoolean(value);
            case "double":
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    log.warn("[GlobalEnvConfig] Failed to parse double value: {}", value);
                    return value;
                }
            case "json":
                // JSON类型保持字符串形式,由使用者自行解析
                return value;
            default:
                return value;
        }
    }


}
