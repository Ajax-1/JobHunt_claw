package com.git.hui.jobclaw.channels.sdk;

/**
 * Uploaded file information after successful CDN upload.
 */
public class UploadedFileInfo {
    private String filekey;
    private String downloadEncryptedQueryParam;
    private String aeskey; // hex-encoded
    private int fileSize; // plaintext size
    private int fileSizeCiphertext; // ciphertext size

    public UploadedFileInfo(String filekey, String downloadEncryptedQueryParam, 
                           String aeskey, int fileSize, int fileSizeCiphertext) {
        this.filekey = filekey;
        this.downloadEncryptedQueryParam = downloadEncryptedQueryParam;
        this.aeskey = aeskey;
        this.fileSize = fileSize;
        this.fileSizeCiphertext = fileSizeCiphertext;
    }

    // Getters
    public String getFilekey() { return filekey; }
    public String getDownloadEncryptedQueryParam() { return downloadEncryptedQueryParam; }
    public String getAeskey() { return aeskey; }
    public int getFileSize() { return fileSize; }
    public int getFileSizeCiphertext() { return fileSizeCiphertext; }
}
