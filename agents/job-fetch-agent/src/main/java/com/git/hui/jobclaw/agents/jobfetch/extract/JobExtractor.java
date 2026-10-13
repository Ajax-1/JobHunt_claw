package com.git.hui.jobclaw.agents.jobfetch.extract;

import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;

import java.util.List;

/**
 * 职位信息提取器接口
 * 用于从文本、文件、CSV、Excel、图片、HTML等中提取职位信息
 *
 * @date 2026/4/18
 */
public interface JobExtractor {

    /**
     * 获取提取器名称
     *
     * @return 提取器名称
     */
    String getName();

    /**
     * 判断是否支持该内容类型
     *
     * @param contentType 内容类型（如 text/plain, application/pdf, image/png 等）
     * @return true 如果支持
     */
    boolean supports(String contentType);

    /**
     * 从文本内容中提取职位信息
     *
     * @param message 文本内容
     * @return 职位信息列表
     */
    List<FetchedJobInfo> extractFromInput(UserConversationInfo userConversationInfo, ChannelReceiveMessage message);

}
