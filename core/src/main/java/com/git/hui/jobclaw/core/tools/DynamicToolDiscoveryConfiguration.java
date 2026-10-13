package com.git.hui.jobclaw.core.tools;

import org.springaicommunity.tool.search.ToolSearchToolCallAdvisor;
import org.springaicommunity.tool.search.ToolSearcher;
import org.springaicommunity.tool.searcher.LuceneToolSearcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具搜索
 * @date 2026/4/24
 */
@Configuration
@EnableConfigurationProperties(DynamicToolDiscoveryProperties.class)
@ConditionalOnProperty(name = "agent.tools.dynamic-discovery.enabled", havingValue = "true", matchIfMissing = true)
public class DynamicToolDiscoveryConfiguration {
    @Bean
    public ToolSearcher toolSearcher(DynamicToolDiscoveryProperties properties) {
        return new LuceneToolSearcher(properties.luceneMinScoreThreshold());
    }

    @Bean
    public ToolSearchToolCallAdvisor toolSearchToolCallAdvisor(ToolSearcher toolSearcher,
                                                               DynamicToolDiscoveryProperties properties) {
        return ToolSearchToolCallAdvisor.builder()
                .toolSearcher(toolSearcher)
                .maxResults(properties.maxResults())
                .build();
    }
}
