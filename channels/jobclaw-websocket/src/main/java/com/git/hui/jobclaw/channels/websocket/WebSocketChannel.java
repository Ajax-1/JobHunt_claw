package com.git.hui.jobclaw.channels.websocket;

import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.AbsStreamChannel;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.channel.ChannelRegistry;
import com.git.hui.jobclaw.core.channel.ChannelResponseMessage;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * WebSocket 通道实现
 * <p>
 * 通过 STOMP over WebSocket 与前端浏览器通信，
 * 支持流式返回、文件/图片消息处理。
 * <p>
 * 消息流程：
 * 前端 WebSocket → adaptToReceive → reportToAgent → MsgRouter → Agent → responseToUser → WebSocket 推送
 */
@Slf4j
public class WebSocketChannel extends AbsStreamChannel<WsMessage> {

    private final WsSessionManager sessionManager;
    private final WebSocketProperties properties;
    private final Resource agentWorkspace;

    public WebSocketChannel(Resource agentWorkspace,
                            ChannelRegistry channelRegistry,
                            ChannelEventPublisher channelEventPublisher,
                            ConfigurationManager configurationManager,
                            WsSessionManager sessionManager,
                            WebSocketProperties properties) {
        super(agentWorkspace, channelRegistry, channelEventPublisher, configurationManager);
        this.agentWorkspace = agentWorkspace;
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    @Override
    public ChannelConfig.ChannelEnum channel() {
        return ChannelConfig.ChannelEnum.WEBSOCKET;
    }

    @Override
    public void activeChannelAccounts() {
        log.info("[WebSocket] Channel activating...");
        // WebSocket 通道由前端主动连接，无需主动轮询
        channelRegistry.registerChannel(this);
        log.info("[WebSocket] Channel registered successfully");
    }

    /**
     * 将前端发来的 WsMessage 转换为 ChannelReceiveMessage
     */
    @Override
    public ChannelReceiveMessage adaptToReceive(MsgWrapper<WsMessage> msgWrapper) {
        if (msgWrapper == null || msgWrapper.getMsg() == null) {
            return null;
        }

        WsMessage wsMsg = msgWrapper.getMsg();
        String jobClawUserId = msgWrapper.getJobClawUserId();
        String fromUserId = jobClawUserId;

        if (StringUtils.isBlank(wsMsg.getMessage()) && isBlankMediaAndFiles(wsMsg)) {
            log.debug("[WebSocket] Skipping empty message");
            return null;
        }

        // 处理文件引用
        List<ChannelReceiveMessage.FileMsg> files = new ArrayList<>();
        if (wsMsg.getFiles() != null) {
            for (String fileId : wsMsg.getFiles()) {
                Path filePath = resolveFileReference(jobClawUserId, fileId);
                if (filePath != null) {
                    files.add(ChannelReceiveMessage.FileMsg.builder()
                            .fileName(fileId)
                            .filePath(filePath)
                            .mimeType("application/octet-stream")
                            .build());
                }
            }
        }

        // 处理媒体引用（图片等）
        List<ChannelReceiveMessage.MediaMsg> medias = new ArrayList<>();
        if (wsMsg.getMedias() != null) {
            for (String mediaId : wsMsg.getMedias()) {
                Path filePath = resolveFileReference(jobClawUserId, mediaId);
                if (filePath != null) {
                    medias.add(ChannelReceiveMessage.MediaMsg.builder()
                            .filePath(filePath)
                            .mimeType(imageMimeType(filePath))
                            .build());
                }
            }
        }

        return ChannelReceiveMessage.builder()
                .msgId(StringUtils.isNotBlank(wsMsg.getMsgId()) ? wsMsg.getMsgId() : "WS_" + System.currentTimeMillis())
                .channel(name())
                .message(wsMsg.getMessage())
                .fromUserId(fromUserId)
                .jobClawUserId(jobClawUserId)
                .medias(medias.isEmpty() ? null : medias)
                .files(files.isEmpty() ? null : files)
                .stream(true)
                .passThrough(Map.of(
                        "sessionId", jobClawUserId,
                        "msgId", wsMsg.getMsgId() != null ? wsMsg.getMsgId() : "WS_" + System.currentTimeMillis()
                ))
                .build();
    }

    // AI-GENERATED AIDEV-NOTE: 保留图片的实际 MIME 类型，PNG 不能硬编码成 JPEG。
    private String imageMimeType(Path path) {
        try {
            String type = Files.probeContentType(path);
            if (type != null && type.startsWith("image/")) return type;
        } catch (java.io.IOException ignored) { }
        return "image/jpeg";
    }

    /**
     * 向用户推送响应消息
     */
    @Override
    public boolean responseToUser(ChannelResponseMessage message) {
        if (message == null) {
            return false;
        }

        String userId = message.getJobClawUserId();
        if (StringUtils.isBlank(userId)) {
            log.warn("[WebSocket] Missing jobClawUserId in response");
            return false;
        }

        String msgId = message.getPassThrough() != null
                ? (String) message.getPassThrough().get("msgId")
                : null;
        if (StringUtils.isBlank(msgId)) {
            msgId = "RSP_" + java.util.UUID.randomUUID();
        }

        try {
            Flux<LlmRspCell> stream = message.getStreamContents();
            if (stream != null) {
                // 流式响应
                sendStreamResponse(userId, msgId, stream);
            } else {
                // 普通文本响应
                String content = StringUtils.isNotBlank(message.getContent())
                        ? message.getContent()
                        : "暂无回复内容";
                WsResponseMessage rsp = WsResponseMessage.text(msgId, content);
                sessionManager.sendToUser(userId, "/queue/rsp", rsp);
            }
            return true;
        } catch (Exception e) {
            log.error("[WebSocket] Failed to send response to user: {}", userId, e);
            return false;
        }
    }

    /**
     * 发送流式响应
     */
    private void sendStreamResponse(String userId, String msgId, Flux<LlmRspCell> stream) {
        StringBuilder thinkingBuf = new StringBuilder();
        StringBuilder contentBuf = new StringBuilder();
        StringBuilder toolBuf = new StringBuilder();
        StringBuilder toolResultBuf = new StringBuilder();

        stream.doOnNext(cell -> {
            if (StringUtils.isNotEmpty(cell.thinking())) {
                thinkingBuf.append(cell.thinking());
                WsResponseMessage rsp = WsResponseMessage.streamChunk(msgId, cell.thinking(), null, null, null);
                sessionManager.sendToUser(userId, "/queue/rsp/stream", rsp);
            }
            // AI-GENERATED AIDEV-NOTE: 纯换行或空格也是有效流式文本，不能丢弃。
            if (StringUtils.isNotEmpty(cell.content())) {
                contentBuf.append(cell.content());
                WsResponseMessage rsp = WsResponseMessage.streamChunk(msgId, null, cell.content(), null, null);
                sessionManager.sendToUser(userId, "/queue/rsp/stream", rsp);
            }
            if (StringUtils.isNotBlank(cell.tool())) {
                toolBuf.append(cell.tool()).append("\n");
                WsResponseMessage rsp = WsResponseMessage.streamChunk(msgId, null, null, cell.tool(), null);
                sessionManager.sendToUser(userId, "/queue/rsp/stream", rsp);
            }
            if (StringUtils.isNotBlank(cell.toolResult())) {
                toolResultBuf.append(cell.toolResult()).append("\n");
                WsResponseMessage rsp = WsResponseMessage.streamChunk(msgId, null, null, null, cell.toolResult());
                sessionManager.sendToUser(userId, "/queue/rsp/stream", rsp);
            }
        }).doOnError(error -> {
            log.error("[WebSocket] Stream error for user: {}", userId, error);
            WsResponseMessage errMsg = WsResponseMessage.error(msgId, userFacingStreamError(error));
            sessionManager.sendToUser(userId, "/queue/rsp/error", errMsg);
        }).doOnComplete(() -> {
            log.debug("[WebSocket] Stream completed for user: {}, contentLen={}", userId, contentBuf.length());
            WsResponseMessage doneMsg = WsResponseMessage.streamDone(msgId);
            sessionManager.sendToUser(userId, "/queue/rsp/done", doneMsg);
        }).subscribe(ignored -> { }, error -> { /* 错误已通过用户队列反馈。 */ });
    }

    /** 将模型配置异常转换成普通用户可以执行的下一步，避免只显示“稍后重试”。 */
    private String userFacingStreamError(Throwable error) {
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

    /**
     * 注册主动回复适配器，供首次画像采集和后台通知使用。
     */
    @Override
    public boolean saveHeartBeatConfig(MsgWrapper<WsMessage> wrapper, boolean force) {
        return force;
    }

    /**
     * 构建主动回复；连接心跳本身仍由 STOMP 管理。
     */
    @Override
    @SuppressWarnings("unchecked")
    public Function<Object, ChannelResponseMessage> buildHeartBeatCallback(String jobClawUserId) {
        // AI-GENERATED AIDEV-NOTE: 首次画像采集通过主动推送返回，必须注册用户回复适配器。
        return response -> {
            var builder = ChannelResponseMessage.builder()
                    .jobClawUserId(jobClawUserId)
                    .toUserId(jobClawUserId)
                    .type(ChannelResponseMessage.ResponseMessageType.TEXT);
            if (response instanceof Flux<?> flux) {
                builder.streamContents((Flux<LlmRspCell>) flux);
            } else {
                builder.content(String.valueOf(response));
            }
            return builder.build();
        };
    }

    /**
     * 解析文件引用 ID 为实际文件路径
     */
    private Path resolveFileReference(String jobClawUserId, String fileId) {
        if (fileId == null || !fileId.matches("[a-fA-F0-9-]{36}(?:\\.[a-zA-Z0-9]{1,10})?")) return null;
        try {
            Path basePath = agentWorkspace.getFile().toPath().resolve("ws").resolve(jobClawUserId);
            Path filePath = basePath.resolve(fileId);
            if (Files.exists(filePath)) {
                return filePath;
            }
            // 尝试带扩展名查找
            try (var files = Files.list(basePath)) {
                return files.filter(f -> f.getFileName().toString().startsWith(fileId + ".")).findFirst().orElse(null);
            }
        } catch (Exception e) {
            log.warn("[WebSocket] Failed to resolve file reference: {}", fileId, e);
            return null;
        }
    }

    private boolean isBlankMediaAndFiles(WsMessage msg) {
        return (msg.getFiles() == null || msg.getFiles().isEmpty())
                && (msg.getMedias() == null || msg.getMedias().isEmpty());
    }
}
