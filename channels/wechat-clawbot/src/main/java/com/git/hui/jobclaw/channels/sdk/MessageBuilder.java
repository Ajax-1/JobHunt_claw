package com.git.hui.jobclaw.channels.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Message builder utilities for constructing Weixin messages.
 */
public class MessageBuilder {

    private static final Logger log = LoggerFactory.getLogger(MessageBuilder.class);

    /**
     * Generate a unique client ID for message tracking.
     */
    public static String generateClientId() {
        return "openclaw-weixin-" + System.currentTimeMillis() + "-" + 
               UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Build a text message request.
     */
    public static WeixinTypes.SendMessageReq buildTextMessage(String toUserId, String text, String contextToken) {
        return buildTextMessage(toUserId, text, contextToken, generateClientId());
    }

    /**
     * Build a text message request with custom client ID.
     */
    public static WeixinTypes.SendMessageReq buildTextMessage(String toUserId, String text, 
                                                               String contextToken, String clientId) {
        WeixinTypes.WeixinMessage msg = new WeixinTypes.WeixinMessage();
        msg.setFromUserId("");
        msg.setToUserId(toUserId);
        msg.setClientId(clientId);
        msg.setMessageType(WeixinTypes.MessageType.BOT);
        msg.setMessageState(WeixinTypes.MessageState.FINISH);
        msg.setContextToken(contextToken);

        List<WeixinTypes.MessageItem> itemList = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            WeixinTypes.MessageItem item = new WeixinTypes.MessageItem();
            item.setType(WeixinTypes.MessageItemType.TEXT);
            WeixinTypes.TextItem textItem = new WeixinTypes.TextItem(text);
            item.setTextItem(textItem);
            itemList.add(item);
        }
        
        msg.setItemList(itemList);

        WeixinTypes.SendMessageReq req = new WeixinTypes.SendMessageReq(msg);
        return req;
    }

    /**
     * Build an image message request.
     */
    public static WeixinTypes.SendMessageReq buildImageMessage(String toUserId, String text,
                                                                UploadedFileInfo uploaded, 
                                                                String contextToken) {
        String clientId = generateClientId();
        
        WeixinTypes.WeixinMessage msg = new WeixinTypes.WeixinMessage();
        msg.setFromUserId("");
        msg.setToUserId(toUserId);
        msg.setClientId(clientId);
        msg.setMessageType(WeixinTypes.MessageType.BOT);
        msg.setMessageState(WeixinTypes.MessageState.FINISH);
        msg.setContextToken(contextToken);

        List<WeixinTypes.MessageItem> itemList = new ArrayList<>();
        
        // Add text caption if provided
        if (text != null && !text.isEmpty()) {
            WeixinTypes.MessageItem textItem = new WeixinTypes.MessageItem();
            textItem.setType(WeixinTypes.MessageItemType.TEXT);
            textItem.setTextItem(new WeixinTypes.TextItem(text));
            itemList.add(textItem);
        }

        // Add image item
        WeixinTypes.MessageItem imageItem = new WeixinTypes.MessageItem();
        imageItem.setType(WeixinTypes.MessageItemType.IMAGE);
        
        WeixinTypes.ImageItem imgItem = new WeixinTypes.ImageItem();
        WeixinTypes.CDNMedia media = new WeixinTypes.CDNMedia();
        media.setEncryptQueryParam(uploaded.getDownloadEncryptedQueryParam());
        // Convert hex to base64
        byte[] aesKeyBytes = hexStringToByteArray(uploaded.getAeskey());
        media.setAesKey(Base64.getEncoder().encodeToString(aesKeyBytes));
        media.setEncryptType(1);
        imgItem.setMedia(media);
        imgItem.setMidSize(uploaded.getFileSizeCiphertext());
        
        imageItem.setImageItem(imgItem);
        itemList.add(imageItem);

        msg.setItemList(itemList);
        return new WeixinTypes.SendMessageReq(msg);
    }

    /**
     * Build a video message request.
     */
    public static WeixinTypes.SendMessageReq buildVideoMessage(String toUserId, String text,
                                                                UploadedFileInfo uploaded,
                                                                String contextToken) {
        String clientId = generateClientId();
        
        WeixinTypes.WeixinMessage msg = new WeixinTypes.WeixinMessage();
        msg.setFromUserId("");
        msg.setToUserId(toUserId);
        msg.setClientId(clientId);
        msg.setMessageType(WeixinTypes.MessageType.BOT);
        msg.setMessageState(WeixinTypes.MessageState.FINISH);
        msg.setContextToken(contextToken);

        List<WeixinTypes.MessageItem> itemList = new ArrayList<>();
        
        // Add text caption if provided
        if (text != null && !text.isEmpty()) {
            WeixinTypes.MessageItem textItem = new WeixinTypes.MessageItem();
            textItem.setType(WeixinTypes.MessageItemType.TEXT);
            textItem.setTextItem(new WeixinTypes.TextItem(text));
            itemList.add(textItem);
        }

        // Add video item
        WeixinTypes.MessageItem videoItem = new WeixinTypes.MessageItem();
        videoItem.setType(WeixinTypes.MessageItemType.VIDEO);
        
        WeixinTypes.VideoItem vidItem = new WeixinTypes.VideoItem();
        WeixinTypes.CDNMedia media = new WeixinTypes.CDNMedia();
        media.setEncryptQueryParam(uploaded.getDownloadEncryptedQueryParam());
        // Convert hex to base64
        byte[] aesKeyBytes = hexStringToByteArray(uploaded.getAeskey());
        media.setAesKey(Base64.getEncoder().encodeToString(aesKeyBytes));
        media.setEncryptType(1);
        vidItem.setMedia(media);
        vidItem.setVideoSize(uploaded.getFileSizeCiphertext());
        
        videoItem.setVideoItem(vidItem);
        itemList.add(videoItem);

        msg.setItemList(itemList);
        return new WeixinTypes.SendMessageReq(msg);
    }

    /**
     * Build a file attachment message request.
     */
    public static WeixinTypes.SendMessageReq buildFileMessage(String toUserId, String text,
                                                               String fileName,
                                                               UploadedFileInfo uploaded,
                                                               String contextToken) {
        String clientId = generateClientId();
        
        WeixinTypes.WeixinMessage msg = new WeixinTypes.WeixinMessage();
        msg.setFromUserId("");
        msg.setToUserId(toUserId);
        msg.setClientId(clientId);
        msg.setMessageType(WeixinTypes.MessageType.BOT);
        msg.setMessageState(WeixinTypes.MessageState.FINISH);
        msg.setContextToken(contextToken);

        List<WeixinTypes.MessageItem> itemList = new ArrayList<>();
        
        // Add text caption if provided
        if (text != null && !text.isEmpty()) {
            WeixinTypes.MessageItem textItem = new WeixinTypes.MessageItem();
            textItem.setType(WeixinTypes.MessageItemType.TEXT);
            textItem.setTextItem(new WeixinTypes.TextItem(text));
            itemList.add(textItem);
        }

        // Add file item
        WeixinTypes.MessageItem fileItem = new WeixinTypes.MessageItem();
        fileItem.setType(WeixinTypes.MessageItemType.FILE);
        
        WeixinTypes.FileItem fItem = new WeixinTypes.FileItem();
        WeixinTypes.CDNMedia media = new WeixinTypes.CDNMedia();
        media.setEncryptQueryParam(uploaded.getDownloadEncryptedQueryParam());
        // Convert hex to base64
        byte[] aesKeyBytes = hexStringToByteArray(uploaded.getAeskey());
        media.setAesKey(Base64.getEncoder().encodeToString(aesKeyBytes));
        media.setEncryptType(1);
        fItem.setMedia(media);
        fItem.setFileName(fileName);
        fItem.setLen(String.valueOf(uploaded.getFileSize()));
        
        fileItem.setFileItem(fItem);
        itemList.add(fileItem);

        msg.setItemList(itemList);
        return new WeixinTypes.SendMessageReq(msg);
    }

    /**
     * Extract text content from a WeixinMessage.
     */
    public static String extractText(WeixinTypes.WeixinMessage msg) {
        if (msg.getItemList() == null || msg.getItemList().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (WeixinTypes.MessageItem item : msg.getItemList()) {
            if (item.getType() != null && item.getType() == WeixinTypes.MessageItemType.TEXT) {
                if (item.getTextItem() != null && item.getTextItem().getText() != null) {
                    sb.append(item.getTextItem().getText());
                }
            } else if (item.getType() != null && item.getType() == WeixinTypes.MessageItemType.VOICE) {
                // Voice-to-text: use text field if available
                if (item.getVoiceItem() != null && item.getVoiceItem().getText() != null) {
                    sb.append(item.getVoiceItem().getText());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Check if a message item is a media type (image, video, file, or voice).
     */
    public static boolean isMediaItem(WeixinTypes.MessageItem item) {
        if (item.getType() == null) return false;
        return item.getType() == WeixinTypes.MessageItemType.IMAGE ||
               item.getType() == WeixinTypes.MessageItemType.VIDEO ||
               item.getType() == WeixinTypes.MessageItemType.FILE ||
               item.getType() == WeixinTypes.MessageItemType.VOICE;
    }

    /**
     * Convert hex string to byte array.
     */
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
