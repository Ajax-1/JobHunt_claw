package com.git.hui.jobclaw.channels.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

/**
 * Weixin protocol types (mirrors proto: GetUpdatesReq/Resp, WeixinMessage, SendMessageReq).
 * API uses JSON over HTTP; bytes fields are base64 strings in JSON.
 */
public class WeixinTypes {

    /** Common request metadata attached to every CGI request. */
    @Data
    public static class BaseInfo {
        @JsonProperty("channel_version")
        private String channelVersion;

        public BaseInfo() {
        }

        public BaseInfo(String channelVersion) {
            this.channelVersion = channelVersion;
        }
    }

    /** Upload media type constants */
    public static class UploadMediaType {
        public static final int IMAGE = 1;
        public static final int VIDEO = 2;
        public static final int FILE = 3;
        public static final int VOICE = 4;
    }

    /** Message type constants */
    public static class MessageType {
        public static final int NONE = 0;
        public static final int USER = 1;
        public static final int BOT = 2;
    }

    /** Message item type constants */
    public static class MessageItemType {
        public static final int NONE = 0;
        public static final int TEXT = 1;
        public static final int IMAGE = 2;
        public static final int VOICE = 3;
        public static final int FILE = 4;
        public static final int VIDEO = 5;
    }

    /** Message state constants */
    public static class MessageState {
        public static final int NEW = 0;
        public static final int GENERATING = 1;
        public static final int FINISH = 2;
    }

    /** Typing status constants */
    public static class TypingStatus {
        public static final int TYPING = 1;
        public static final int CANCEL = 2;
    }

    // ==================== Request Types ====================

    /** GetUploadUrl request */
    @Data
    public static class GetUploadUrlReq {
        private String filekey;

        @JsonProperty("media_type")
        private Integer mediaType;

        @JsonProperty("to_user_id")
        private String toUserId;

        private Integer rawsize;

        @JsonProperty("rawfilemd5")
        private String rawfilemd5;

        private Integer filesize;

        @JsonProperty("thumb_rawsize")
        private Integer thumbRawsize;

        @JsonProperty("thumb_rawfilemd5")
        private String thumbRawfilemd5;

        @JsonProperty("thumb_filesize")
        private Integer thumbFilesize;

        @JsonProperty("no_need_thumb")
        private Boolean noNeedThumb;

        private String aeskey;

        @JsonProperty("base_info")
        private BaseInfo baseInfo;
    }

    /** GetUploadUrl response */
    @Data
    public static class GetUploadUrlResp {
        @JsonProperty("upload_param")
        private String uploadParam;

        @JsonProperty("thumb_upload_param")
        private String thumbUploadParam;

        @JsonProperty("upload_full_url")
        private String uploadFullUrl;
    }

    /** Text item */
    @Data
    public static class TextItem {
        private String text;

        public TextItem() {
        }

        public TextItem(String text) {
            this.text = text;
        }
    }

    /** CDN media reference; aes_key is base64-encoded bytes in JSON. */
    @Data
    public static class CDNMedia {
        @JsonProperty("encrypt_query_param")
        private String encryptQueryParam;

        @JsonProperty("aes_key")
        private String aesKey;

        @JsonProperty("encrypt_type")
        private Integer encryptType;

        @JsonProperty("full_url")
        private String fullUrl;
    }

    /** Image item */
    @Data
    public static class ImageItem {
        private CDNMedia media;

        @JsonProperty("thumb_media")
        private CDNMedia thumbMedia;

        private String aeskey;
        private String url;

        @JsonProperty("mid_size")
        private Integer midSize;

        @JsonProperty("thumb_size")
        private Integer thumbSize;

        @JsonProperty("thumb_height")
        private Integer thumbHeight;

        @JsonProperty("thumb_width")
        private Integer thumbWidth;

        @JsonProperty("hd_size")
        private Integer hdSize;

        @JsonProperty("local_path")
        private Path localPath;
    }

    /** Voice item */
    @Data
    public static class VoiceItem {
        private CDNMedia media;

        @JsonProperty("encode_type")
        private Integer encodeType;

        @JsonProperty("bits_per_sample")
        private Integer bitsPerSample;

        @JsonProperty("sample_rate")
        private Integer sampleRate;

        private Integer playtime;
        private String text;

        @JsonProperty("local_path")
        private Path localPath;
    }

    /** File item */
    @Data
    public static class FileItem {
        private CDNMedia media;

        @JsonProperty("file_name")
        private String fileName;

        private String md5;
        private String len;

        private Path localPath;
    }

    /** Video item */
    @Data
    public static class VideoItem {
        private CDNMedia media;

        @JsonProperty("video_size")
        private Integer videoSize;

        @JsonProperty("play_length")
        private Integer playLength;

        @JsonProperty("video_md5")
        private String videoMd5;

        @JsonProperty("thumb_media")
        private CDNMedia thumbMedia;

        @JsonProperty("thumb_size")
        private Integer thumbSize;

        @JsonProperty("thumb_height")
        private Integer thumbHeight;

        @JsonProperty("thumb_width")
        private Integer thumbWidth;

        private Path localPath;
    }

    /** Referenced message */
    public static class RefMessage {
        @JsonProperty("message_item")
        private MessageItem messageItem;
        private String title;

        // Getters and Setters
        public MessageItem getMessageItem() {
            return messageItem;
        }

        public void setMessageItem(MessageItem messageItem) {
            this.messageItem = messageItem;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    /** Message item */
    @Data
    public static class MessageItem {
        private Integer type;

        @JsonProperty("create_time_ms")
        private Long createTimeMs;

        @JsonProperty("update_time_ms")
        private Long updateTimeMs;

        @JsonProperty("is_completed")
        private Boolean isCompleted;

        @JsonProperty("msg_id")
        private String msgId;

        @JsonProperty("ref_msg")
        private RefMessage refMsg;

        @JsonProperty("text_item")
        private TextItem textItem;

        @JsonProperty("image_item")
        private ImageItem imageItem;

        @JsonProperty("voice_item")
        private VoiceItem voiceItem;

        @JsonProperty("file_item")
        private FileItem fileItem;

        @JsonProperty("video_item")
        private VideoItem videoItem;
    }

    /** Unified message (proto: WeixinMessage) */
    @Data
    public static class WeixinMessage {
        private Long seq;

        @JsonProperty("message_id")
        private Long messageId;

        @JsonProperty("from_user_id")
        private String fromUserId;

        @JsonProperty("to_user_id")
        private String toUserId;

        @JsonProperty("client_id")
        private String clientId;

        @JsonProperty("create_time_ms")
        private Long createTimeMs;

        @JsonProperty("update_time_ms")
        private Long updateTimeMs;

        @JsonProperty("delete_time_ms")
        private Long deleteTimeMs;

        @JsonProperty("session_id")
        private String sessionId;

        @JsonProperty("group_id")
        private String groupId;

        @JsonProperty("message_type")
        private Integer messageType;

        @JsonProperty("message_state")
        private Integer messageState;

        @JsonProperty("item_list")
        private List<MessageItem> itemList;

        @JsonProperty("context_token")
        private String contextToken;
    }

    /** GetUpdates request */
    @Data
    public static class GetUpdatesReq {
        @JsonProperty("get_updates_buf")
        private String getUpdatesBuf;

        @JsonProperty("base_info")
        private BaseInfo baseInfo;

        public GetUpdatesReq() {
        }

        public GetUpdatesReq(String getUpdatesBuf, BaseInfo baseInfo) {
            this.getUpdatesBuf = getUpdatesBuf;
            this.baseInfo = baseInfo;
        }
    }

    /** GetUpdates response */
    @Data
    public static class GetUpdatesResp {
        private Integer ret;
        private Integer errcode;
        private String errmsg;
        private List<WeixinMessage> msgs;

        @JsonProperty("sync_buf")
        private String syncBuf;

        @JsonProperty("get_updates_buf")
        private String getUpdatesBuf;

        @JsonProperty("longpolling_timeout_ms")
        private Integer longpollingTimeoutMs;

    }

    /** SendMessage request */
    @Data
    public static class SendMessageReq {
        private WeixinMessage msg;

        @JsonProperty("base_info")
        private BaseInfo baseInfo;

        public SendMessageReq() {
        }

        public SendMessageReq(WeixinMessage msg) {
            this.msg = msg;
        }
    }

    /** SendTyping request */
    @Data
    public static class SendTypingReq {
        @JsonProperty("ilink_user_id")
        private String ilinkUserId;

        @JsonProperty("typing_ticket")
        private String typingTicket;

        private Integer status;

        @JsonProperty("base_info")
        private BaseInfo baseInfo;

        public SendTypingReq() {
        }

        public SendTypingReq(String ilinkUserId, String typingTicket, Integer status) {
            this.ilinkUserId = ilinkUserId;
            this.typingTicket = typingTicket;
            this.status = status;
        }
    }

    /** GetConfig response */
    @Data
    public static class GetConfigResp {
        private Integer ret;
        private String errmsg;

        @JsonProperty("typing_ticket")
        private String typingTicket;
    }

    // ==================== Bot Binding Types ====================

    /** Bind QR code response (get_bot_qrcode) */
    @Data
    public static class BindQrCodeResp {
        private String qrcode;

        @JsonProperty("qrcode_img_content")
        private String qrcodeImgContent;

        private Integer ret;
        private String errmsg;
    }

    /** QR code status response (get_qrcode_status) */
    @Data
    public static class QrCodeStatusResp {
        private String status; // wait, scaned, expired, confirmed

        @JsonProperty("bot_token")
        private String botToken;

        @JsonProperty("ilink_bot_id")
        private String ilinkBotId;

        @JsonProperty("ilink_user_id")
        private String ilinkUserId;

        private String baseurl;

        private Integer ret;
        private String errmsg;
    }
}
