package com.git.hui.jobclaw.core.router;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.llm.LlmCaller;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.bus.event.MessageReceivedEvent;
import com.git.hui.jobclaw.core.bus.event.MessageResponseEvent;
import com.git.hui.jobclaw.core.bus.event.UserConnectedEvent;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.cli.SystemCommandDispatcher;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.AgentRouter;
import com.git.hui.jobclaw.core.router.intent.IntentClassifier;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import com.git.hui.jobclaw.core.router.intent.classifier.IntentClassificationRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 消息网关路由，所有channel传入的消息都需要先经过MsgRouter来实现路由转发到具体的Agent，执行相关业务操作，然后再将消息分发出去
 *
 * @date 2026/4/17
 */
@Slf4j
@Component
public class MsgRouter {

    private final ChannelRegistry channelRegistry;

    private final ChannelEventPublisher channelEventPublisher;

    private final LlmCaller llmCaller;

    private final IIdentityAgent identityAgent;

    // ==== Intent & Agent Routing ====
    private final IntentClassifier intentClassifier;
    private final AgentRouter agentRouter;
    private final SessionAgentBinder sessionBinder;
    private final AgentRegistry agentRegistry;
    private final SystemCommandDispatcher commandDispatcher;

    public MsgRouter(ChannelRegistry channelRegistry,
                     ChannelEventPublisher channelEventPublisher,
                     LlmCaller simpleLlmCaller,
                     IIdentityAgent identityAgent,
                     IntentClassifier compositeIntentClassifier,
                     AgentRouter agentRouter,
                     SessionAgentBinder sessionBinder,
                     AgentRegistry agentRegistry,
                     SystemCommandDispatcher commandDispatcher) {
        this.channelRegistry = channelRegistry;
        this.channelEventPublisher = channelEventPublisher;
        this.llmCaller = simpleLlmCaller;
        this.identityAgent = identityAgent;
        this.intentClassifier = compositeIntentClassifier;
        this.agentRouter = agentRouter;
        this.sessionBinder = sessionBinder;
        this.agentRegistry = agentRegistry;
        this.commandDispatcher = commandDispatcher;
    }

    /**
     * 处理消息接收事件
     * 流程：身份检查 → 意图识别 → Agent路由 → 执行响应
     */
    @Async
    @EventListener
    public void onMessageReceived(MessageReceivedEvent event) {
        // AI-GENERATED AIDEV-NOTE: 异步路由异常也要回复用户，避免前端无限等待。
        try {
            handleMessageReceived(event);
        } catch (Exception e) {
            log.error("处理用户消息失败: {}", event.getOriginalMessage().getMsgId(), e);
            sendTextResponse(event.getOriginalMessage(), userFacingLlmError(e));
        }
    }

    private void handleMessageReceived(MessageReceivedEvent event) {
        var msg = event.getOriginalMessage();
        String jobClawUserId = msg.getJobClawUserId();
        String fromUserId = msg.getFromUserId();
        String channel = msg.getChannel();
        String userMessage = msg.getMessage();
        UserConversationInfo conversationInfo = new UserConversationInfo(jobClawUserId, channel, fromUserId, msg.isGroupTalk());
        String conversationId = conversationInfo.conversationId();

        // Step 2: 系统命令处理 - 使用统一的命令调度器
        if (commandDispatcher.isSystemCommand(userMessage)) {
            if (commandDispatcher.executeCommand(msg, conversationInfo, userMessage, str -> sendTextResponse(msg, str))) {
                log.info("System command executed for user: {} to Response: {}", jobClawUserId, userMessage);
                return;
            }
            // 如果命令调度器无法处理，继续后续流程
        }

        // AI-GENERATED AIDEV-NOTE: 帮助及切换命令无需画像，普通对话仍执行身份采集。
        if (!msg.isGroupTalk() && identityAgent.triggerToCollectIdentity(conversationInfo, userMessage)) {
            log.info("Message handled by unified initializer for user: {}", jobClawUserId);
            return;
        }

        // step 2.5: 如果使用的是 /command + 文案，则表示主动切换使用某个BizAgent
        if (userMessage.startsWith("/")) {
            List<BizAgent> bizAgents = agentRegistry.getAllAgents(jobClawUserId);
            for (BizAgent bizAgent : bizAgents) {
                if (userMessage.startsWith("/" + bizAgent.getAgentIntro().getAgentId())) {
                    log.info("User {} switches to agent {}", jobClawUserId, bizAgent.getAgentIntro().getAgentId());
                    routeToAgent(bizAgent.getAgentIntro().getAgentId(), msg, conversationInfo);
                    return;
                }
            }
        }

        // Step 3: 判断是否需要意图识别
        if (!sessionBinder.needsIntentRecognition(jobClawUserId, conversationId, userMessage)) {
            // 继续使用绑定的Agent
            String agentId = sessionBinder.getBoundAgentId(jobClawUserId, conversationId).orElse(null);
            routeToAgent(agentId, msg, conversationInfo);
            return;
        }

        // Step 4: 意图识别
        IntentClassificationRes classification = intentClassifier.classify(conversationInfo, userMessage, java.util.Collections.emptyList());
        log.info("Intent classification: {}", classification);


        // Step 5: 路由到Agent
        Optional<String> currentBoundAgent = sessionBinder.getBoundAgentId(jobClawUserId, conversationId);
        AgentRouter.RouterResult routeResult = agentRouter.route(classification, currentBoundAgent);

        // Step 8: 绑定会话状态（如果是新会话）
        if (routeResult.isNewSession()) {
            sessionBinder.bind(jobClawUserId, conversationId, routeResult.agentId());
            sessionBinder.addIntentHistory(jobClawUserId, conversationId, classification.intentType(), classification.confidence());
        }

        // Step 9: 执行Agent
        routeToAgent(routeResult.agentId(), msg, conversationInfo);
    }

    /**
     * 路由到指定Agent执行
     */
    private void routeToAgent(String agentId, ChannelReceiveMessage msg, UserConversationInfo conversationInfo) {
        // 获取Agent
        BizAgent agent = agentRegistry.getAgent(agentId).orElseGet(() -> agentRegistry.getDefaultAgent().orElse(null));

        if (agent == null) {
            log.error("未找到Agent: {}", agentId);
            sendTextResponse(msg, "没有找到可用的Agent哦，请稍后再试。");
            return;
        }

        log.info("Agent 开始执行用户请求：{}", agent.getAgentIntro());
        // 保存当前Agent到用户对话信息
        conversationInfo.setAgent(agent.getAgentIntro().getAgentId());
        // 执行Agent
        try {
            String response = null;
            Flux<LlmRspCell> streamRes = null;
            if (msg.isStream()) {
                streamRes = agent.stream(conversationInfo, msg);
            } else {
                response = agent.process(conversationInfo, msg);
            }

            if (response == null && streamRes == null) {
                // Agent处理失败，回退到LLM响应
                fallbackToLlm(msg, conversationInfo);
                return;
            }

            sendTextResponse(msg, response, streamRes);
        } catch (Exception e) {
            log.error("Agent执行失败: {}", agentId, e);
            fallbackToLlm(msg, conversationInfo);
        }
    }

    /**
     * 回退到LLM响应
     */
    private void fallbackToLlm(ChannelReceiveMessage msg, UserConversationInfo conversationInfo) {
        try {
            if (msg.isStream()) {
                Flux<LlmRspCell> streamRes = llmCaller.stream(conversationInfo, new Prompt(msg.getMessage()), LlmRspCell::of);
                sendTextResponse(msg, null, streamRes);
            } else {
                var response = llmCaller.call(conversationInfo, new Prompt(msg.getMessage()));
                sendTextResponse(msg, response);
            }
        } catch (Exception e) {
            log.error("LLM响应失败", e);
            sendTextResponse(msg, userFacingLlmError(e));
        }
    }

    /** 将模型配置异常转换成普通用户可以执行的下一步，避免只显示“稍后重试”。 */
    private String userFacingLlmError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? "" : cause.getMessage();
        String normalized = message.toLowerCase(Locale.ROOT);
        if (message.contains("未找到厂商配置")
                || message.contains("厂商未配置模型")
                || message.contains("未找到模型配置")
                || message.contains("未配置默认模型")
                || message.contains("模型配置格式错误")
                || message.contains("未找到模型提供者")
                || normalized.contains("api key")
                || normalized.contains("apikey")) {
            return "尚未配置可用的 AI 服务商，请管理员进入“平台配置 → LLM供应商”配置文本模型和 API Key 后重试。";
        }
        return "AI 服务暂时不可用，请稍后重试；如果问题持续，请检查管理员的模型配置。";
    }

    private boolean sendTextResponse(ChannelReceiveMessage msg, String content) {
        return this.sendTextResponse(msg, content, null);
    }

    /**
     * 发送文本响应
     */
    private boolean sendTextResponse(ChannelReceiveMessage msg, String content, Flux<LlmRspCell> streamContents) {
        ChannelResponseMessage responseMessage = ChannelResponseMessage.builder()
                .jobClawUserId(msg.getJobClawUserId())
                .toUserId(msg.getFromUserId())
                .type(ChannelResponseMessage.ResponseMessageType.TEXT)
                .content(content)
                .streamContents(streamContents)
                .passThrough(msg.getPassThrough())
                .build();

        channelEventPublisher.publishMessageResponse(
                "RSP_" + System.currentTimeMillis(),
                msg.getMsgId(),
                msg.getChannel(),
                responseMessage
        );
        return true;
    }

    /**
     * 处理消息响应事件 - 发送响应到通道
     */
    @Async
    @EventListener
    public void onMessageResponse(MessageResponseEvent event) {
        // 这里接收到业务Agent的执行返回，此时我们需要将返回结果发送到对应的通道中
        // 这里需要通过 ChannelRegistry 找到对应的通道，然后执行消息响应
        var channel = channelRegistry.getChannel(event.getChannel());
        if (channel == null) {
            log.error("找不到对应的通道，请确认这个通道是否正常注册：{}", event.getChannel());
            return;
        }
        log.debug("Publishing MessageResponseEvent: responseId={}, channel={}, msg={}",
                event.getResponseId(),
                event.getChannel(),
                event.getResponseMessage());
        channel.responseToUser(event.getResponseMessage());
    }


    /**
     * 接收到用户连接事件
     *
     * @param event
     */
    @Async
    @EventListener
    public void onImBotConnected(UserConnectedEvent event) {
        // 给用户发送一个欢迎的消息
        String template = """
                您已经成功联通JobClaw啦，现在您可以直接通过对话和JobClaw进行沟通了~
                """;
        // 发送一条欢迎语句给连接用户
        channelEventPublisher.publishProactiveMessage("HI_" + System.currentTimeMillis(),
                event.getUserId(),
                event.getChannel(),
                template);
    }
}
