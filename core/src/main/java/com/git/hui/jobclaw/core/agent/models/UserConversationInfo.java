package com.git.hui.jobclaw.core.agent.models;

import com.git.hui.jobclaw.core.utils.MD5Utils;

//public record UserConversationInfo(String jobClawUserId, String channel, String conversationId, boolean group) {
//
//}

public class UserConversationInfo {
    private String jobClawUserId;
    private String channel;
    /**
     * 会话id
     */
    private String conversationId;
    /**
     * 是否群聊
     */
    private boolean group;

    private String agent;

    public UserConversationInfo(String jobClawUserId, String channel, String conversationId, boolean group) {
        this.jobClawUserId = jobClawUserId;
        this.channel = channel;
        this.conversationId = conversationId;
        this.group = group;
    }

    public String jobClawUserId() {
        return jobClawUserId;
    }

    public String channel() {
        return channel;
    }

    public String conversationId() {
        return conversationId;
    }

    public boolean group() {
        return group;
    }

    public UserConversationInfo setAgent(String agent) {
        this.agent = agent;
        return this;
    }

    public String agent() {
        return agent;
    }

    public static UserConversationInfo parse(String conversationId) {
        // 原始的 conversationId 是按照 jobClawUserId:channel:conversationId:group 的格式进行组装的，所以我们首先进行解析，将会话的JobClawUserId依然保存，用于用户会话的隔离

        String[] parts = conversationId.split(":", 5);
        var userConversation = new UserConversationInfo(parts[0], parts[1], parts[2], "1".equals(parts[3]));
        userConversation.setAgent(parts[4]);
        return userConversation;
    }

    public static String generateConversationId(String jobClawUserId, String channel, String conversationId, boolean group, String agent) {
        // 由于用户传入的 conversationId 可能存在各种格式，为了统一，我们使用md5进行修剪
        return jobClawUserId + ":" + channel + ":" + MD5Utils.md5(conversationId) + ":" + (group ? "1" : "0") + ":" + agent;
    }

    public String genId() {
        return generateConversationId(jobClawUserId, channel, conversationId, group, agent);
    }
}