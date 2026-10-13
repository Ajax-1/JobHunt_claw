package com.git.hui.jobclaw.core.apis.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.Data;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 请求上下文，携带用户身份相关信息
 *
 * @date 2022/7/6
 */
public class ReqInfoContext {
    public static final String REQ_INFO_KEY = "agentSseEmitter";
    private static TransmittableThreadLocal<ReqInfo> contexts = new TransmittableThreadLocal<>();

    public static void addReqInfo(ReqInfo reqInfo) {
        contexts.set(reqInfo);
    }

    public static void clear() {
        contexts.remove();
    }

    public static ReqInfo getReqInfo() {
        return contexts.get();
    }

    @Data
    public static class ReqInfo implements Principal {
        /**
         * appKey
         */
        private String appKey;
        /**
         * 访问的域名
         */
        private String host;
        /**
         * 访问路径
         */
        private String path;
        /**
         * 客户端ip
         */
        private String clientIp;
        /**
         * referer
         */
        private String referer;
        /**
         * post 表单参数
         */
        private String payload;
        /**
         * 设备信息
         */
        private String userAgent;

        /**
         * 登录的会话
         */
        private String session;

        /**
         * 用户id
         */
        private Long userId;

        /**
         * 用户信息
         */
        private UserBo user;

        private String deviceId;

        /**
         * 消息数量
         */
        private Integer msgNum;

        /**
         * 当前聊天的会话id
         */
        private String chatId;

        private Map<String, Object> context;

        @Override
        public String getName() {
            return session;
        }

        public void addContextVar(String key, Object value) {
            if (context == null) {
                context = new HashMap<>();
            }
            context.put(key, value);
        }

        public Object getContextVar(String key) {
            if (context == null) {
                return null;
            }
            return context.get(key);
        }
    }
}
