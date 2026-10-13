package com.git.hui.jobclaw.core.agent;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 业务Agent抽象接口，用于路由
 *
 * 核心职责：
 * 1. 定义Agent的唯一标识
 * 2. 声明支持处理的意图类型
 * 3. 处理用户消息
 *
 * @date 2026/4/17
 */
public interface BizAgent {

    /**
     * 获取Agent权限
     */
    AgentPermission permission();


    /**
     * 获取Agent唯一标识
     * AIDEV-NOTE: Agent ID应该全局唯一，建议使用小写字母+中划线格式
     */
    AgentIntro getAgentIntro();

    /**
     * 获取Agent支持处理的意图类型列表
     * AIDEV-NOTE: 返回空数组表示支持所有意图类型
     */
    default List<AgentIntro> getSupportedIntents() {
        return List.of(getAgentIntro());
    }

    /**
     * 检查Agent是否支持处理指定意图
     */
    default boolean supportsIntent(AgentIntro intentType) {
        for (var supportedType : getSupportedIntents()) {
            if (supportedType.equals(intentType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理用户消息
     *
     * @param message 接收到的用户消息
     * @param userConversationInfo 用户会话信息
     * @return 是否处理成功，返回false表示无法处理该消息
     */
    String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message);

    /**
     * 流式处理用户消息
     *
     * @param message 接收到的用户消息
     * @param userConversationInfo 用户会话信息
     * @return 是否处理成功，返回false表示无法处理该消息
     */
    Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message);

    /**
     * 获取Agent优先级（用于多Agent场景下的选择）
     * 值越大优先级越高
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 检查Agent是否可用
     * 当Agent依赖外部服务（如数据库、API）时可以用于健康检查
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 初始化Agent（可选实现）
     * 在Agent首次注册时调用，用于加载配置等
     */
    default void initialize() {
        // 默认空实现
    }

    /**
     * 销毁Agent（可选实现）
     * 在Agent注销时调用，用于资源清理
     */
    default void destroy() {
        // 默认空实现
    }


    interface AgentIntro {
        String getAgentId();

        /**
         * 获取Agent的一句话描述
         */
        String getIntro();

        /**
         * 获取Agent的详细描述
         */
        String getDescription();
    }
}
