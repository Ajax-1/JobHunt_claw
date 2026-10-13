package com.git.hui.jobclaw.core.utils;

/**
 *
 * @date 2026/4/21
 */
public class MimeUtils {

    public static String mimeByFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf(".");
        if (index < 0) {
            return "text/plain";
        }
        String ext = fileName.substring(index + 1);
        return mimeByExt(ext);
    }

    public static String mimeByExt(String ext) {
        switch (ext) {
            case "txt":
                return "text/plain";
            case "html":
                return "text/html";
            case "htm":
                return "text/html";
            case "xml":
                return "text/xml";
            case "json":
                return "application/json";
            case "js":
                return "application/javascript";
            case "css":
                return "text/css";
            case "md":
                return "text/markdown";
            case "yaml":
                return "text/yaml";
            case "yml":
                return "text/yaml";
            case "tson":
                return "text/plain";
            case "rar":
                return "application/x-rar-compressed";
            case "zip":
                return "application/zip";
            case "csv":
                return "text/csv";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pdf":
                return "application/pdf";
            case "jpg":
                return "image/jpeg";
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "ico":
                return "image/x-icon";
            case "svg":
                return "image/svg+xml";
            case "bmp":
                return "image/bmp";
            case "tif":
                return "image/tiff";
            case "tiff":
                return "image/tiff";
            case "mp4":
                return "video/mp4";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "mpeg":
                return "video/mpeg";
            case "mpg":
                return "video/mpeg";
            case "m4":
                return "video/mp4";
            default:
                // 默认全部认为普通文本
                return "text/plain";
        }
    }

}
