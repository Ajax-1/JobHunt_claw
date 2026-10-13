package com.git.hui.jobclaw.core.channel;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 接收消息
 * @date 2026/4/8
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class ChannelReceiveMessage {
    private String msgId;
    private String channel;
    private String message;
    private String fromUserId;

    /**
     * JobClaw用户ID，用于获取对应的用户偏好信息
     */
    private String jobClawUserId;

    private List<MediaMsg> medias;
    private List<FileMsg> files;

    /**
     * 透传参数，channel上报的接收消息，当JobClaw处理之后，会生成一个/多个响应消息，每个响应消息都会携带这个参数；用于解决不同渠道的传参个性化
     */
    private Map<String, Object> passThrough;

    /**
     * 是否流式处理，默认是否，表示同步调用大模型
     * 是否支持流式返回，主要取决于通信Channel，是否支持这种场景
     */
    @Builder.Default
    private boolean stream = false;

    /**
     * 是否为群聊
     */
    @Builder.Default
    private boolean groupTalk = false;


    @Data
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    public static class MediaMsg {
        private String downUrl;
        private String fileType;
        private Path filePath;
        private String mimeType;
        private byte[] data; // Optional: inline image data
    }

    @Data
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    public static class FileMsg {
        private String downUrl;
        private Path filePath;
        private String fileName;
        private String fileType;
        private String mimeType;
    }
}
