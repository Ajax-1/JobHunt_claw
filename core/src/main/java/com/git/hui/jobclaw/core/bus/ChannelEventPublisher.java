package com.git.hui.jobclaw.core.bus;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import reactor.core.publisher.Flux;

/**
 * 通道事件发布器接口 - 用于发布 IM 通道相关的事件
 *
 * <p>基于 Spring ApplicationEventPublisher 实现，支持以下事件类型：
 * <ul>
 *   <li>消息接收事件：IM 收到用户消息后发布</li>
 *   <li>消息响应事件：Agent 处理完成后发布响应</li>
 *   <li>主动推送事件：后台任务主动推送消息给用户</li>
 *   <li>用户连接/断开事件：用户绑定/解绑通道时发布</li>
 * </ul>
 *
 * <p>AIDEV-NOTE: 统一的事件发布接口，实现消息总线模式，解耦通道层与业务层
 *
 * @date 2026/4/8
 */
public interface ChannelEventPublisher {

    /**
     * 发布消息接收事件
     *
     * <p>当 IM 通道接收到用户消息时调用，触发后续的消息处理流程。
     *
     * @param channel 通道名称（如 wechat-clawbot、dingding）
     * @param message 接收到的消息对象，包含用户ID、消息内容等
     * @param needReply 是否需要回复，true 表示需要 Agent 处理后回复，false 表示仅记录
     */
    void publishMessageReceived(String channel, ChannelReceiveMessage message, boolean needReply);

    /**
     * 发布消息响应事件
     *
     * <p>当 Agent 处理完用户消息并生成响应后调用，将响应消息推送到对应的 IM 通道。
     *
     * @param responseId 响应消息的唯一ID，用于追踪和去重
     * @param relatedMessageId 关联的原始消息ID，用于建立请求-响应关系
     * @param channel 目标通道名称
     * @param responseMessage 响应消息对象，包含回复内容、类型等
     */
    void publishMessageResponse(String responseId, String relatedMessageId, String channel, ChannelResponseMessage responseMessage);

    /**
     * 发布主动推送消息事件（简化版）
     *
     * <p>用于后台任务主动向用户推送消息（如定时提醒、岗位推荐等）。
     * 此方法使用默认优先级（0），适用于大多数场景。
     *
     * @param responseId 推送消息的唯一ID
     * @param jobClawUserId 目标用户的 JobClaw 用户ID
     * @param channelName 目标通道名称
     * @param response 推送的文本内容
     * @return true 表示成功发布事件，false 表示用户未绑定该通道或发布失败
     */
    boolean publishProactiveMessage(String responseId, String jobClawUserId, String channelName, String response);


    boolean publishProactiveMessage(String responseId, String jobClawUserId, String channelName, Flux<LlmRspCell> response);

    /**
     * 发布主动推送消息事件（完整版）
     *
     * <p>用于后台任务主动向用户推送消息，支持设置优先级和完整的消息对象。
     * 优先级高的消息会优先处理和发送。
     *
     * @param responseId 推送消息的唯一ID
     * @param channelName 目标通道名称
     * @param responseMessage 响应消息对象，可包含富媒体内容
     * @param priority 消息优先级，数值越大优先级越高（0=普通，1=高，2=紧急）
     */
    void publishProactiveMessage(String responseId, String channelName, ChannelResponseMessage responseMessage, int priority);

    /**
     * 发布用户连接事件
     *
     * <p>当用户首次绑定 IM 通道或重新连接时调用，用于初始化用户会话和状态。
     *
     * @param channel 通道名称
     * @param userId 用户在通道中的唯一标识（如微信 openid、钉钉 userId）
     * @param channelUser 通道配置信息，包含用户偏好、API Key 等
     * @param isNewUser 是否为新用户，true 表示首次绑定，false 表示重新连接
     * @param sourceIp 用户来源 IP 地址，用于安全审计
     */
    void publishUserConnected(String channel, String userId, ChannelConfig channelUser, boolean isNewUser, String sourceIp);

    /**
     * 发布用户断开连接事件
     *
     * <p>当用户解绑 IM 通道或主动断开连接时调用，用于清理用户会话和资源。
     *
     * @param channel 通道名称
     * @param channelUser 通道配置信息
     * @param reason 断开原因（如 user_unbind、timeout、error 等）
     */
    void publishUserDisconnected(String channel, ChannelConfig channelUser, String reason);
}
