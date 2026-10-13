package com.git.hui.jobclaw.agents.jobfetch.crawler.impl;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于搜索的方案，查找实岗位信息
 *
 * @date 2026/4/18
 */
@Slf4j
public class WebSearchCrawler implements JobCrawler {
    private static final int MAX_CHAT_CNT = 20;

    protected final JobLlmCaller jobLlmCaller;

    protected BeanOutputConverter<ArrayList<FetchedJobInfo>> gatherResConverter;
    protected final Resource promptResource;

    public WebSearchCrawler(JobLlmCaller jobLlmCaller,
                            Resource promptResource) {
        this.jobLlmCaller = jobLlmCaller;
        this.promptResource = promptResource;
        gatherResConverter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public String getName() {
        return "WebSearchCrawler";
    }

    @Override
    public boolean supports(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        // fixme 基于搜索引擎的方案、到官网抓取
        return url.startsWith("https://") || url.startsWith("http://");
    }

    @Override
    public List<FetchedJobInfo> crawl(UserConversationInfo userConversationInfo, String url, String originMsg) {
        log.info("开始爬取URL! {} -> {}", originMsg, url);
        return List.of();
    }
}
