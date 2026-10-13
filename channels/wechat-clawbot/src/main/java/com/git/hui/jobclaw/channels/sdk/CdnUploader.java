package com.git.hui.jobclaw.channels.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Utilities for uploading files to Weixin CDN with AES-128-ECB encryption.
 */
public class CdnUploader {

    private static final Logger log = LoggerFactory.getLogger(CdnUploader.class);

    private final WeixinApiClient apiClient;
    private final String cdnBaseUrl;

    public CdnUploader(WeixinApiClient apiClient, String cdnBaseUrl) {
        this.apiClient = apiClient;
        this.cdnBaseUrl = cdnBaseUrl;
    }

    /**
     * Calculate AES-128-ECB padded size (PKCS7 padding).
     */
    private static int aesEcbPaddedSize(int rawSize) {
        int blockSize = 16;
        int padding = blockSize - (rawSize % blockSize);
        return rawSize + padding;
    }

    /**
     * Generate random hex string.
     */
    private static String generateHexKey(int bytes) {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[bytes];
        random.nextBytes(key);
        return bytesToHex(key);
    }

    /**
     * Convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Calculate MD5 hash of data.
     */
    private static String calculateMD5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data);
        return bytesToHex(digest);
    }

    /**
     * Encrypt data using AES-128-ECB with PKCS7 padding.
     */
    private static byte[] aesEcbEncrypt(byte[] plaintext, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(plaintext);
    }

    /**
     * Upload a file to Weixin CDN.
     * 
     * @param filePath Path to the local file
     * @param toUserId Target user ID
     * @param mediaType Media type (IMAGE, VIDEO, FILE, VOICE)
     * @return UploadedFileInfo with upload details
     */
    public UploadedFileInfo uploadFile(String filePath, String toUserId, int mediaType) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }

        // Read file content
        byte[] plaintext = Files.readAllBytes(file.toPath());
        int rawsize = plaintext.length;
        String rawfilemd5 = calculateMD5(plaintext);
        int filesize = aesEcbPaddedSize(rawsize);
        
        // Generate encryption parameters
        String filekey = generateHexKey(16);
        byte[] aeskeyBytes = new byte[16];
        new SecureRandom().nextBytes(aeskeyBytes);
        String aeskey = bytesToHex(aeskeyBytes);

        log.debug("Uploading file: {} rawsize={} filesize={} md5={} filekey={}", 
                  filePath, rawsize, filesize, rawfilemd5, filekey);

        // Get upload URL from API
        WeixinTypes.GetUploadUrlReq uploadReq = new WeixinTypes.GetUploadUrlReq();
        uploadReq.setFilekey(filekey);
        uploadReq.setMediaType(mediaType);
        uploadReq.setToUserId(toUserId);
        uploadReq.setRawsize(rawsize);
        uploadReq.setRawfilemd5(rawfilemd5);
        uploadReq.setFilesize(filesize);
        uploadReq.setNoNeedThumb(true);
        uploadReq.setAeskey(aeskey);

        WeixinTypes.GetUploadUrlResp uploadResp = apiClient.getUploadUrl(uploadReq);
        
        String uploadFullUrl = uploadResp.getUploadFullUrl();
        String uploadParam = uploadResp.getUploadParam();
        
        if (uploadFullUrl == null && uploadParam == null) {
            throw new IllegalStateException("getUploadUrl returned no upload URL");
        }

        // Encrypt and upload to CDN
        byte[] ciphertext = aesEcbEncrypt(plaintext, aeskeyBytes);
        String downloadParam = uploadToCdn(ciphertext, uploadFullUrl, uploadParam, filekey);

        log.info("File uploaded successfully: filekey={} size={}", filekey, rawsize);

        return new UploadedFileInfo(
            filekey,
            downloadParam,
            aeskey,
            rawsize,
            filesize
        );
    }

    /**
     * Upload encrypted data to CDN.
     */
    private String uploadToCdn(byte[] encryptedData, String uploadFullUrl, 
                               String uploadParam, String filekey) throws Exception {
        // TODO: Implement actual HTTP PUT upload to CDN
        // For now, return the upload param as the download param
        // In production, you would:
        // 1. PUT the encrypted data to uploadFullUrl or construct URL from uploadParam
        // 2. Receive response with download parameters
        
        log.warn("CDN upload not fully implemented. Using placeholder.");
        return uploadParam != null ? uploadParam : "";
    }

    /**
     * Upload an image file.
     */
    public UploadedFileInfo uploadImage(String filePath, String toUserId) throws Exception {
        return uploadFile(filePath, toUserId, WeixinTypes.UploadMediaType.IMAGE);
    }

    /**
     * Upload a video file.
     */
    public UploadedFileInfo uploadVideo(String filePath, String toUserId) throws Exception {
        return uploadFile(filePath, toUserId, WeixinTypes.UploadMediaType.VIDEO);
    }

    /**
     * Upload a file attachment (non-image, non-video).
     */
    public UploadedFileInfo uploadFileAttachment(String filePath, String fileName, String toUserId) throws Exception {
        return uploadFile(filePath, toUserId, WeixinTypes.UploadMediaType.FILE);
    }
}
