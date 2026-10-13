package com.git.hui.jobclaw.agents.jobfetch.crawler;

import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;

import java.util.List;

/**
 * 职位爬虫接口
 * 用于从网络中爬取职位信息
 *
 * @date 2026/4/18
 */
public interface JobCrawler {

    /**
     * 获取爬虫名称
     *
     * @return 爬虫名称
     */
    String getName();

    /**
     * 判断是否支持该URL
     *
     * @param url 目标URL
     * @return true 如果支持
     */
    boolean supports(String url);

    /**
     * 从指定URL爬取职位信息
     *
     * @param url 目标URL
     * @return 职位信息列表
     */
    List<FetchedJobInfo> crawl(UserConversationInfo userConversationInfo, String url, String originMsg);
}
