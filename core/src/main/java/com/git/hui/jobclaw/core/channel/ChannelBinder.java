package com.git.hui.jobclaw.core.channel;

import java.util.List;
import java.util.Map;

/**
 *
 * @date 2026/4/10
 */
public interface ChannelBinder {

    /**
     * 获取所有绑定的账号
     *
     * @return key = userId, value = 绑定的信息
     */
    Map<Long, List<ChannelConfig>> getAllAccounts();


    /**
     * 绑定账号
     *
     * @param userId
     * @param channelInfo
     */
    void bindAccount(Long userId, ChannelConfig channelInfo);

}
