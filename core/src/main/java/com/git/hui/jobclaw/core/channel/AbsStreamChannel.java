package com.git.hui.jobclaw.core.channel;

import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import org.springframework.core.io.Resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支持流式返回的通道
 * @date 2026/4/16
 */
public abstract class AbsStreamChannel<T> extends AbsChannel<T> {
    protected ActiveAiCardCache aiCardStatus = new ActiveAiCardCache();


    public AbsStreamChannel(Resource agentWorkspace, ChannelRegistry channelRegistry, ChannelEventPublisher channelEventPublisher, ConfigurationManager configurationManager) {
        super(agentWorkspace, channelRegistry, channelEventPublisher, configurationManager);
    }

    public enum AiCardStatus {
        INIT,
        ANSWERING,
        COMPLETE,
        ;
    }

    public record AiCardState(AiCardStatus status, Long updateTime) {
    }


    public static class ActiveAiCardCache {
        private final Map<String, Map<String, AiCardState>> aiCardStatus = new ConcurrentHashMap<>();

        private String buildKey(String robotId, String jobClawUserId) {
            return robotId + "_" + jobClawUserId;
        }

        public void startAiCard(String robotId, String jobClawUserId, String cardId) {
            aiCardStatus.computeIfAbsent(buildKey(robotId, jobClawUserId),
                            key -> new ConcurrentHashMap<>())
                    .put(cardId, new AiCardState(AiCardStatus.INIT, System.currentTimeMillis()));
        }

        public void answerAiCard(String robotId, String jobClawUserId, String cardId) {
            String key = buildKey(robotId, jobClawUserId);
            var map = aiCardStatus.get(key);
            if (map != null) {
                map.put(cardId, new AiCardState(AiCardStatus.ANSWERING, System.currentTimeMillis()));
            }
        }

        public void finishAiCard(String robotId, String jobClawUserId, String cardId) {
            String key = buildKey(robotId, jobClawUserId);
            var map = aiCardStatus.get(key);
            if (map != null) {
                map.remove(cardId);
            }
        }

        public String getActiveAiCard(String robotId, String jobClawUserId) {
            // 查找时间最大的一个初始化状态的aiCard
            String key = buildKey(robotId, jobClawUserId);
            var map = aiCardStatus.get(key);
            if (map == null) {
                return null;
            }
            for (Map.Entry<String, AiCardState> entry : map.entrySet()) {
                if (entry.getValue().status == AiCardStatus.INIT) {
                    return entry.getKey();
                }
            }
            return null;
        }
    }
}
