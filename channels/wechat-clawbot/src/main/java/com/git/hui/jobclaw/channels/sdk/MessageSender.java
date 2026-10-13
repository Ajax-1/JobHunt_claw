package com.git.hui.jobclaw.channels.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level message sender for Weixin ClawBot.
 * Provides convenient methods to send different types of messages.
 */
public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    private final WeixinApiClient apiClient;
    private final CdnUploader cdnUploader;

    public MessageSender(WeixinApiClient apiClient, CdnUploader cdnUploader) {
        this.apiClient = apiClient;
        this.cdnUploader = cdnUploader;
    }

    /**
     * Send a text message.
     * 
     * @param toUserId Target user ID
     * @param text Message text
     * @param contextToken Context token from incoming message
     * @return Message client ID
     */
    public String sendTextMessage(String toUserId, String text, String contextToken) throws Exception {
        if (contextToken == null || contextToken.isEmpty()) {
            log.warn("sendTextMessage: contextToken missing for to={}, sending without context", toUserId);
        }

        WeixinTypes.SendMessageReq req = MessageBuilder.buildTextMessage(toUserId, text, contextToken);
        
        try {
            apiClient.sendMessage(req);
            String clientId = req.getMsg().getClientId();
            log.info("Text message sent successfully: to={} clientId={}", toUserId, clientId);
            return clientId;
        } catch (Exception e) {
            log.error("Failed to send text message: to={} err={}", toUserId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send an image message with optional text caption.
     * 
     * @param toUserId Target user ID
     * @param text Optional text caption
     * @param imagePath Path to local image file
     * @param contextToken Context token from incoming message
     * @return Message client ID
     */
    public String sendImageMessage(String toUserId, String text, String imagePath, String contextToken) throws Exception {
        if (contextToken == null || contextToken.isEmpty()) {
            log.warn("sendImageMessage: contextToken missing for to={}, sending without context", toUserId);
        }

        log.info("Uploading image: path={} to={}", imagePath, toUserId);
        UploadedFileInfo uploaded = cdnUploader.uploadImage(imagePath, toUserId);
        log.info("Image upload done: filekey={} size={}", uploaded.getFilekey(), uploaded.getFileSize());

        WeixinTypes.SendMessageReq req = MessageBuilder.buildImageMessage(toUserId, text, uploaded, contextToken);
        
        try {
            apiClient.sendMessage(req);
            String clientId = req.getMsg().getClientId();
            log.info("Image message sent successfully: to={} clientId={}", toUserId, clientId);
            return clientId;
        } catch (Exception e) {
            log.error("Failed to send image message: to={} err={}", toUserId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send a video message with optional text caption.
     * 
     * @param toUserId Target user ID
     * @param text Optional text caption
     * @param videoPath Path to local video file
     * @param contextToken Context token from incoming message
     * @return Message client ID
     */
    public String sendVideoMessage(String toUserId, String text, String videoPath, String contextToken) throws Exception {
        if (contextToken == null || contextToken.isEmpty()) {
            log.warn("sendVideoMessage: contextToken missing for to={}, sending without context", toUserId);
        }

        log.info("Uploading video: path={} to={}", videoPath, toUserId);
        UploadedFileInfo uploaded = cdnUploader.uploadVideo(videoPath, toUserId);
        log.info("Video upload done: filekey={} size={}", uploaded.getFilekey(), uploaded.getFileSize());

        WeixinTypes.SendMessageReq req = MessageBuilder.buildVideoMessage(toUserId, text, uploaded, contextToken);
        
        try {
            apiClient.sendMessage(req);
            String clientId = req.getMsg().getClientId();
            log.info("Video message sent successfully: to={} clientId={}", toUserId, clientId);
            return clientId;
        } catch (Exception e) {
            log.error("Failed to send video message: to={} err={}", toUserId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send a file attachment with optional text caption.
     * 
     * @param toUserId Target user ID
     * @param text Optional text caption
     * @param filePath Path to local file
     * @param fileName Display name for the file
     * @param contextToken Context token from incoming message
     * @return Message client ID
     */
    public String sendFileMessage(String toUserId, String text, String filePath, String fileName, String contextToken) throws Exception {
        if (contextToken == null || contextToken.isEmpty()) {
            log.warn("sendFileMessage: contextToken missing for to={}, sending without context", toUserId);
        }

        log.info("Uploading file: path={} name={} to={}", filePath, fileName, toUserId);
        UploadedFileInfo uploaded = cdnUploader.uploadFileAttachment(filePath, fileName, toUserId);
        log.info("File upload done: filekey={} size={}", uploaded.getFilekey(), uploaded.getFileSize());

        WeixinTypes.SendMessageReq req = MessageBuilder.buildFileMessage(toUserId, text, fileName, uploaded, contextToken);
        
        try {
            apiClient.sendMessage(req);
            String clientId = req.getMsg().getClientId();
            log.info("File message sent successfully: to={} clientId={}", toUserId, clientId);
            return clientId;
        } catch (Exception e) {
            log.error("Failed to send file message: to={} err={}", toUserId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send typing indicator to show bot is processing.
     * 
     * @param ilinkUserId Target user ID
     * @param typingTicket Typing ticket from getConfig
     * @param status TypingStatus.TYPING or TypingStatus.CANCEL
     */
    public void sendTyping(String ilinkUserId, String typingTicket, int status) throws Exception {
        WeixinTypes.SendTypingReq req = new WeixinTypes.SendTypingReq();
        req.setIlinkUserId(ilinkUserId);
        req.setTypingTicket(typingTicket);
        req.setStatus(status);

        try {
            apiClient.sendTyping(req);
            log.debug("Typing indicator sent: userId={} status={}", ilinkUserId, status);
        } catch (Exception e) {
            log.error("Failed to send typing indicator: userId={} err={}", ilinkUserId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get typing ticket for a user.
     * 
     * @param ilinkUserId Target user ID
     * @param contextToken Context token
     * @return Typing ticket
     */
    public String getTypingTicket(String ilinkUserId, String contextToken) throws Exception {
        WeixinTypes.GetConfigResp resp = apiClient.getConfig(ilinkUserId, contextToken);
        return resp.getTypingTicket();
    }
}
