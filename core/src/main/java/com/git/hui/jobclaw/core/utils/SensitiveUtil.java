package com.git.hui.jobclaw.core.utils;

/**
 *
 * @date 2026/4/22
 */
public class SensitiveUtil {

    public static String securityReturn(String key) {
        // 脱敏处理
        if (key.length() < 3) {
            return "***";
        }
        if (key.length() < 5) {
            return key.charAt(0) + "***" + key.substring(key.length() - 1);
        } else {
            return key.substring(0, 2) + "***" + key.substring(key.length() - 2);
        }
    }
}
