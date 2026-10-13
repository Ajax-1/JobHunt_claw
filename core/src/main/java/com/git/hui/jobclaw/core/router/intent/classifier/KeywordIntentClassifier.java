package com.git.hui.jobclaw.core.router.intent.classifier;

import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.cli.SystemCommandDispatcher;
import com.git.hui.jobclaw.core.router.intent.IntentClassifier;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于关键词的意图分类器
 *
 * AIDEV-NOTE: 适用于明确业务场景，响应快，成本低
 * 支持配置化扩展
 *
 * @date 2026/4/17
 */
@Slf4j
@Component
public class KeywordIntentClassifier implements IntentClassifier {

    private final SystemCommandDispatcher commandDispatcher;

    // 意图类型 -> 关键词列表（按优先级排序）
    // AIDEV-NOTE: 可配置化，后续抽到配置文件中
    private static final Map<PresetAgentIntro, List<KeywordEntry>> KEYWORDS = Map.of(
            PresetAgentIntro.COLLECT, List.of(
                    new KeywordEntry("投递", 1.0),
                    new KeywordEntry("投简历", 1.0),
                    new KeywordEntry("岗位信息", 0.9),
                    new KeywordEntry("面试", 0.7),
                    new KeywordEntry("校招", 0.6),
                    new KeywordEntry("社招", 0.6),
                    new KeywordEntry("秋招", 0.6),
                    new KeywordEntry("春招", 0.6),
                    new KeywordEntry("实习", 0.5)
            ),
            PresetAgentIntro.RECOMMEND, List.of(
                    new KeywordEntry("推荐", 1.0),
                    new KeywordEntry("帮我看看", 0.9),
                    new KeywordEntry("有什么岗位", 0.8),
                    new KeywordEntry("有什么工作", 0.8),
                    new KeywordEntry("求推荐", 1.0),
                    new KeywordEntry("工作推荐", 1.0)
            ),
            PresetAgentIntro.SUBSCRIBE, List.of(
                    new KeywordEntry("订阅", 1.0),
                    new KeywordEntry("通知", 0.8),
                    new KeywordEntry("提醒", 0.8),
                    new KeywordEntry("推送", 0.7),
                    new KeywordEntry("上新", 0.6)
            ),
            PresetAgentIntro.QUERY, List.of(
                    new KeywordEntry("查询", 1.0),
                    new KeywordEntry("查看", 0.8),
                    new KeywordEntry("状态", 0.7),
                    new KeywordEntry("进度", 0.7),
                    new KeywordEntry("投递记录", 1.0),
                    new KeywordEntry("面试状态", 1.0)
            ),
            PresetAgentIntro.PROFILE, List.of(
                    new KeywordEntry("偏好", 1.0),
                    new KeywordEntry("设置", 0.8),
                    new KeywordEntry("信息", 0.6),
                    new KeywordEntry("修改", 0.7),
                    new KeywordEntry("更新", 0.7)
            )
    );

    // 命令词映射
    private static final Map<String, PresetAgentIntro> COMMANDS = Map.of(
            "/collect", PresetAgentIntro.COLLECT,
            "/recommend", PresetAgentIntro.RECOMMEND,
            "/subscribe", PresetAgentIntro.SUBSCRIBE,
            "/query", PresetAgentIntro.QUERY,
            "/profile", PresetAgentIntro.PROFILE
    );


    public KeywordIntentClassifier(SystemCommandDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }

    @Override
    public IntentClassificationRes classify(UserConversationInfo userConversationInfo, String message, List<String> conversationHistory) {
        if (message == null || message.isBlank()) {
            return IntentClassificationRes.unknown("空消息");
        }

        String normalized = message.toLowerCase().trim();

        // 1. 先检查系统命令CLI - 使用统一的命令调度器
        Optional<PresetAgentIntro> commandIntent = commandDispatcher.recognizeIntent(message);
        if (commandIntent.isPresent()) {
            return IntentClassificationRes.highConfidence(
                    commandIntent.get(),
                    "命令匹配");
        }

        // 2. 再检查预设的业务场景
        for (Map.Entry<String, PresetAgentIntro> entry : COMMANDS.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return IntentClassificationRes.highConfidence(
                        entry.getValue(),
                        "命令匹配: " + entry.getKey());
            }
        }

        // 3. 关键词匹配
        double maxScore = 0.0;
        PresetAgentIntro bestType = PresetAgentIntro.UNKNOWN;
        String matchedKeyword = "";

        for (Map.Entry<PresetAgentIntro, List<KeywordEntry>> entry : KEYWORDS.entrySet()) {
            PresetAgentIntro type = entry.getKey();
            List<KeywordEntry> keywords = entry.getValue();

            for (KeywordEntry kw : keywords) {
                if (normalized.contains(kw.keyword())) {
                    double score = kw.weight();
                    if (score > maxScore) {
                        maxScore = score;
                        bestType = type;
                        matchedKeyword = kw.keyword();
                    }
                }
            }
        }

        // 3. 根据置信度返回结果
        if (maxScore >= 0.9) {
            return IntentClassificationRes.highConfidence(bestType,
                    "关键词匹配: " + matchedKeyword);
        } else if (maxScore >= 0.7) {
            return new IntentClassificationRes(bestType, maxScore,
                    "关键词匹配: " + matchedKeyword);
        } else if (maxScore >= 0.5) {
            return new IntentClassificationRes(bestType, maxScore,
                    "关键词匹配(低置信): " + matchedKeyword);
        }

        // 4. 检查对话历史上下文
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            // AIDEV-NOTE: 可以根据最近几轮对话来推断意图
            // 这里简化处理，返回UNKNOWN让后续的LLM分类器处理
        }

        return IntentClassificationRes.unknown("未匹配到关键词");
    }

    /**
     * 关键词条目
     *
     * @param keyword 关键词
     * @param weight 权重 0-1
     */
    private record KeywordEntry(String keyword, double weight) {
    }
}