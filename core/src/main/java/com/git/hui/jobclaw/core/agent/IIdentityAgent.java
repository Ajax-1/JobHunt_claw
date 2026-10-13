package com.git.hui.jobclaw.core.agent;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 *
 * @date 2026/4/17
 */
public interface IIdentityAgent {

    /**
     * 尝试触发用户信息采集
     *
     * @param conversation 会话信息
     * @param userMessage 用户输入
     * @return true 表示成功触发用户信息收集，会自动进入采集模式，false 表示未触发，请自行处理
     */
    boolean triggerToCollectIdentity(UserConversationInfo conversation, String userMessage);

    /**
     * 基于用户偏好设置，构建机器人的偏好特征
     *
     * @param jobClawUserId 用户id
     * @return 系统提示语
     */
    String buildSoulPrompt(String jobClawUserId);


    /**
     * 基于用户偏好设置，构建专有的系统提示语
     *
     * @param jobClawUserId 用户id
     * @return 系统提示语
     */
    String buildSystemPrompt(String jobClawUserId);


    /**
     * 异步更新用户的偏好信息
     *
     * @param conversation 会话信息
     * @param messages 会话内容
     */
    void asyncUpdateUserIdentityAsync(UserConversationInfo conversation, List<Message> messages);
}
