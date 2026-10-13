package com.git.hui.jobclaw.core.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Cache;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LocalCacheManager {

    private final ConcurrentHashMap<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定名称的缓存。默认 TTL 5 分钟，最大 1000 条目。
     */
    public Cache<String, Object> getCache(String name) {
        return caches.computeIfAbsent(name, k -> CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .recordStats()
                .build());
    }

    /**
     * 获取或创建带自定义配置的缓存。
     */
    public Cache<String, Object> getCache(String name, Duration expireAfterWrite, int maxSize) {
        return caches.computeIfAbsent(name, k -> CacheBuilder.newBuilder()
                .expireAfterWrite(expireAfterWrite)
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }

    @SuppressWarnings("unchecked")
    public <V> V get(String cacheName, String key) {
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache == null) return null;
        return (V) cache.getIfPresent(key);
    }

    public void put(String cacheName, String key, Object value) {
        getCache(cacheName).put(key, value);
    }

    public void remove(String cacheName, String key) {
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) cache.invalidate(key);
    }

    public void clearAll() {
        caches.values().forEach(Cache::invalidateAll);
        caches.clear();
        log.info("[LocalCacheManager] All caches cleared");
    }

    @PreDestroy
    public void destroy() {
        clearAll();
    }
}
