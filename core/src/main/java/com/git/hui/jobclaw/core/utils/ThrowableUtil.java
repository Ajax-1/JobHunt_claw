package com.git.hui.jobclaw.core.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 异常工具类 - 用于处理 Throwable 相关操作
 * @date 2026/4/9
 */
public class ThrowableUtil {

    /**
     * 将异常的堆栈信息转换为字符串（默认最多100行）
     * @param throwable 异常对象
     * @return 堆栈信息的字符串表示
     */
    public static String getStackTrace(Throwable throwable) {
        return getStackTrace(throwable, 100);
    }

    /**
     * 将异常的堆栈信息转换为字符串（指定最大行数）
     * @param throwable 异常对象
     * @param maxLines 最大行数限制
     * @return 堆栈信息的字符串表示
     */
    public static String getStackTrace(Throwable throwable, int maxLines) {
        if (throwable == null) {
            return "";
        }
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String fullStackTrace = sw.toString();
        
        // 如果不需要限制行数或堆栈行数未超过限制，直接返回
        if (maxLines <= 0) {
            return fullStackTrace;
        }
        
        // 按行分割并限制行数
        String[] lines = fullStackTrace.split("\n");
        if (lines.length <= maxLines) {
            return fullStackTrace;
        }
        
        // 截取前 maxLines 行
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]);
            if (i < maxLines - 1) {
                sb.append("\n");
            }
        }
        sb.append("\n... (堆栈信息已截断，共 ").append(lines.length).append(" 行，仅显示前 ").append(maxLines).append(" 行)");
        
        return sb.toString();
    }

    /**
     * 将异常的堆栈信息转换为字符串（包含自定义消息，默认最多100行）
     * @param message 自定义消息
     * @param throwable 异常对象
     * @return 包含消息和堆栈信息的字符串
     */
    public static String getStackTrace(String message, Throwable throwable) {
        return getStackTrace(message, throwable, 100);
    }

    /**
     * 将异常的堆栈信息转换为字符串（包含自定义消息，指定最大行数）
     * @param message 自定义消息
     * @param throwable 异常对象
     * @param maxLines 最大行数限制
     * @return 包含消息和堆栈信息的字符串
     */
    public static String getStackTrace(String message, Throwable throwable, int maxLines) {
        StringBuilder sb = new StringBuilder();
        if (message != null && !message.isEmpty()) {
            sb.append(message).append("\n");
        }
        sb.append(getStackTrace(throwable, maxLines));
        return sb.toString();
    }

    /**
     * 获取异常的简要信息（仅异常类名和消息，不包含堆栈）
     * @param throwable 异常对象
     * @return 简要异常信息
     */
    public static String getSimpleMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        return throwable.getClass().getName() + ": " + throwable.getMessage();
    }
}
