package com.git.hui.jobclaw.channels.sdk;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Media downloader and decryptor for Weixin CDN.
 * Handles downloading and AES-128-ECB decryption of images, videos, files, and voice messages.
 */
public class MediaDownloader {

    private static final Logger log = LoggerFactory.getLogger(MediaDownloader.class);

    private final String cdnBaseUrl;

    private final String mediaDir;
    private final CloseableHttpClient httpClient;

    public MediaDownloader(String cdnBaseUrl, String mediaDir) {
        this.cdnBaseUrl = cdnBaseUrl;
        this.mediaDir = mediaDir;
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Download and decrypt media from CDN.
     *
     * @param encryptQueryParam CDN encryption query parameter
     * @param aesKeyBase64 AES key in base64 format
     * @param fullUrl Optional full download URL (preferred if available)
     * @param label Label for logging
     * @return Decrypted media bytes
     */
    public byte[] downloadAndDecrypt(String encryptQueryParam, String aesKeyBase64,
                                     String fullUrl, String label) throws Exception {
        // Parse AES key (handles both raw 16 bytes and hex-encoded formats)
        byte[] aesKey = parseAesKey(aesKeyBase64, label);

        // Build download URL
        String url;
        if (fullUrl != null && !fullUrl.isEmpty()) {
            url = fullUrl;
        } else {
            url = buildCdnDownloadUrl(encryptQueryParam);
        }

        log.debug("{}: downloading from {}", label, url);

        // Download encrypted data
        byte[] encrypted = downloadBytes(url, label);
        log.debug("{}: downloaded {} bytes, decrypting", label, encrypted.length);

        // Decrypt using AES-128-ECB
        byte[] decrypted = decryptAesEcb(encrypted, aesKey);
        log.debug("{}: decrypted {} bytes", label, decrypted.length);

        return decrypted;
    }

    /**
     * Download plain (unencrypted) bytes from CDN.
     */
    public byte[] downloadPlain(String encryptQueryParam, String fullUrl, String label) throws Exception {
        String url;
        if (fullUrl != null && !fullUrl.isEmpty()) {
            url = fullUrl;
        } else {
            url = buildCdnDownloadUrl(encryptQueryParam);
        }

        log.debug("{}: downloading plain from {}", label, url);
        return downloadBytes(url, label);
    }

    /**
     * Download image and save to file.
     *
     * @param imageItem Image item from message
     * @param fileName Output file name (null for auto-generated)
     * @return Path to saved file
     */
    public Path downloadImage(WeixinTypes.ImageItem imageItem, String fileName) throws Exception {
        if (imageItem == null || imageItem.getMedia() == null) {
            throw new IllegalArgumentException("Invalid image item");
        }

        WeixinTypes.CDNMedia media = imageItem.getMedia();
        String aesKey = imageItem.getAeskey() != null ?
                Base64.getEncoder().encodeToString(hexStringToByteArray(imageItem.getAeskey())) :
                media.getAesKey();

        byte[] decrypted = downloadAndDecrypt(
                media.getEncryptQueryParam(),
                aesKey,
                media.getFullUrl(),
                "downloadImage"
        );

        if (fileName == null) {
            fileName = "image_" + System.currentTimeMillis() + ".jpg";
        }

        Path filePath = Paths.get(mediaDir, fileName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, decrypted);

        log.info("Image saved to: {}", filePath);
        return filePath;
    }

    /**
     * Download video and save to file.
     */
    public Path downloadVideo(WeixinTypes.VideoItem videoItem, String fileName) throws Exception {
        if (videoItem == null || videoItem.getMedia() == null) {
            throw new IllegalArgumentException("Invalid video item");
        }

        WeixinTypes.CDNMedia media = videoItem.getMedia();

        byte[] decrypted = downloadAndDecrypt(
                media.getEncryptQueryParam(),
                media.getAesKey(),
                media.getFullUrl(),
                "downloadVideo"
        );

        if (fileName == null) {
            fileName = "video_" + System.currentTimeMillis() + ".mp4";
        }

        Path filePath = Paths.get(mediaDir, fileName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, decrypted);

        log.info("Video saved to: {}", filePath);
        return filePath;
    }

    /**
     * Download file attachment and save to file.
     */
    public Path downloadFile(WeixinTypes.FileItem fileItem, String fileName) throws Exception {
        if (fileItem == null || fileItem.getMedia() == null) {
            throw new IllegalArgumentException("Invalid file item");
        }

        WeixinTypes.CDNMedia media = fileItem.getMedia();

        byte[] decrypted = downloadAndDecrypt(
                media.getEncryptQueryParam(),
                media.getAesKey(),
                media.getFullUrl(),
                "downloadFile"
        );

        if (fileName == null) {
            fileName = fileItem.getFileName() != null ? fileItem.getFileName() :
                    "file_" + System.currentTimeMillis();
        }

        Path filePath = Paths.get(mediaDir, fileName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, decrypted);

        log.info("File saved to: {}", filePath);
        return filePath;
    }

    /**
     * Download voice message and optionally transcode from SILK to WAV.
     *
     * @param voiceItem Voice item from message
     * @param transcodeToWav Whether to transcode SILK to WAV
     * @return Path to saved audio file
     */
    public Path downloadVoice(WeixinTypes.VoiceItem voiceItem, boolean transcodeToWav) throws Exception {
        if (voiceItem == null || voiceItem.getMedia() == null) {
            throw new IllegalArgumentException("Invalid voice item");
        }

        WeixinTypes.CDNMedia media = voiceItem.getMedia();

        byte[] decrypted = downloadAndDecrypt(
                media.getEncryptQueryParam(),
                media.getAesKey(),
                media.getFullUrl(),
                "downloadVoice"
        );

        String fileName;
        byte[] audioData = decrypted;

        // Transcode SILK to WAV if requested
        if (transcodeToWav) {
            try {
                audioData = silkToWav(decrypted);
                fileName = "voice_" + System.currentTimeMillis() + ".wav";
                log.info("Voice transcoded from SILK to WAV");
            } catch (Exception e) {
                log.warn("SILK to WAV transcode failed, using raw SILK: {}", e.getMessage());
                fileName = "voice_" + System.currentTimeMillis() + ".silk";
            }
        } else {
            fileName = "voice_" + System.currentTimeMillis() + ".silk";
        }

        Path filePath = Paths.get(mediaDir, fileName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, audioData);

        log.info("Voice saved to: {}", filePath);
        return filePath;
    }

    /**
     * Parse AES key from base64 string.
     * Handles two formats:
     * 1. base64(raw 16 bytes) - used by images
     * 2. base64(hex string of 16 bytes) - used by files/voice/video
     */
    private byte[] parseAesKey(String aesKeyBase64, String label) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(aesKeyBase64);

        if (decoded.length == 16) {
            // Direct 16-byte key
            return decoded;
        }

        if (decoded.length == 32) {
            // Hex-encoded key: base64 → hex string → raw bytes
            String hexString = new String(decoded, "ASCII");
            if (hexString.matches("[0-9a-fA-F]{32}")) {
                return hexStringToByteArray(hexString);
            }
        }

        throw new IllegalArgumentException(String.format(
                "%s: aes_key must decode to 16 raw bytes or 32-char hex string, got %d bytes",
                label, decoded.length
        ));
    }

    /**
     * Build CDN download URL from encrypted query parameter.
     */
    private String buildCdnDownloadUrl(String encryptQueryParam) {
        return cdnBaseUrl + "/" + encryptQueryParam;
    }

    /**
     * Download bytes from URL.
     */
    private byte[] downloadBytes(String url, String label) throws IOException {
        HttpGet request = new HttpGet(url);

        return httpClient.execute(request, response -> {
            int statusCode = response.getCode();
            if (statusCode < 200 || statusCode >= 300) {
                String body = EntityUtils.toString(response.getEntity());
                throw new IOException(String.format("%s: CDN download %d %s body=%s",
                        label, statusCode, response.getReasonPhrase(), body));
            }

            return EntityUtils.toByteArray(response.getEntity());
        });
    }

    /**
     * Decrypt data using AES-128-ECB with PKCS5 padding.
     */
    private byte[] decryptAesEcb(byte[] encrypted, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(encrypted);
    }

    /**
     * Convert hex string to byte array.
     */
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Simple SILK to WAV transcoder.
     * Note: This is a placeholder. For production, use a proper SILK decoder library.
     *
     * @param silkBuf SILK encoded audio data
     * @return WAV formatted audio data
     */
    private byte[] silkToWav(byte[] silkBuf) throws Exception {
        // TODO: Implement proper SILK decoding
        // Options:
        // 1. Use JNI to call libsilk
        // 2. Use a Java SILK decoder library
        // 3. Call external silk decoder binary

        log.warn("SILK to WAV transcode not fully implemented. Returning raw SILK data.");
        // For now, just wrap in a basic WAV header (won't actually play)
        // In production, integrate with silk-wasm or libsilk

        return silkBuf; // Placeholder - return as-is
    }

    /**
     * Close the HTTP client.
     */
    public void close() throws IOException {
        httpClient.close();
    }
}
