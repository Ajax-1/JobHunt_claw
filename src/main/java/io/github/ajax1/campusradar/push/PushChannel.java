package io.github.ajax1.campusradar.push;

/**
 * 推送渠道。P1 只有企业微信群机器人；个人微信、飞书、邮件以后按需加。
 */
public interface PushChannel {

    boolean enabled();

    /**
     * 发送一条 markdown 消息，失败抛异常
     */
    void sendMarkdown(String markdown);
}
