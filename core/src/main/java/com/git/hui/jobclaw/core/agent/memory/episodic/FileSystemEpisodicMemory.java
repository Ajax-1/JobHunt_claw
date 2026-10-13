package com.git.hui.jobclaw.core.agent.memory.episodic;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.git.hui.jobclaw.core.agent.llm.LlmCaller;
import com.git.hui.jobclaw.core.agent.memory.ContextWindowProperties;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.utils.FileUtils;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 基于文件系统的情景记忆实现
 *
 * <p>存储路径：{@code workspace/users/{userId}/episodic-memory.yaml}
 *
 * <p>工作流程：
 * <ol>
 *   <li>record: 异步调用 LLM 从对话中提取结构化事实，保存为 YAML</li>
 *   <li>retrieve: 加载事实列表，按关键词匹配 + 时间衰减排序，返回相关记忆文本</li>
 * </ol>
 *
 * <p>AIDEV-NOTE: Phase 3 — 情景记忆文件系统实现，使用简单关键词匹配作为起步
 */
@Component
public class FileSystemEpisodicMemory {

    private static final Logger log = LoggerFactory.getLogger(FileSystemEpisodicMemory.class);
    private static final int MAX_CONVERSATION_FOR_EXTRACT = 30;
    private static final int MAX_RETRIEVE_FACTS = 10;

    private final Path usersDir;
    private final ContextWindowProperties properties;
    private final LlmCaller llmCaller;
    private final String extractPromptTemplate;

    public FileSystemEpisodicMemory(
            @Value("${agent.workspace:Unknown}") Resource workspaceDir,
            ContextWindowProperties properties,
            @Value("classpath:/prompts/episodic-extract-prompt.md") Resource promptResource,
            LlmCaller simpleLlmCaller) throws IOException {
        this.usersDir = workspaceDir.getFile().toPath().resolve("users");
        this.properties = properties;
        this.llmCaller = simpleLlmCaller;

        try {
            this.extractPromptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
            log.info("FileSystemEpisodicMemory initialized, episodicEnabled={}", properties.isEpisodicEnabled());
        } catch (IOException e) {
            log.error("Failed to load episodic extraction prompt", e);
            throw new RuntimeException("Failed to initialize FileSystemEpisodicMemory", e);
        }
    }

    // ==================== record ====================


    public void asyncRecord(UserConversationInfo user, List<Message> conversation) {
        try {
            record(user.jobClawUserId(), conversation);
        } catch (Exception e) {
            log.warn("Failed to trigger episodic recording for user: {}", user.jobClawUserId(), e);
        }
    }

    public void record(String userId, List<Message> conversation) {
        if (!properties.isEpisodicEnabled()) return;
        if (conversation == null || conversation.size() < properties.getEpisodicMinConversationLength()) {
            log.debug("[Episodic] Skipping record for user {}: conversation too short ({})",
                    userId, conversation == null ? 0 : conversation.size());
            return;
        }

        // 异步提取，避免阻塞主流程
        CompletableFuture.runAsync(() -> {
            try {
                List<EpisodicFact> newFacts = extractFacts(userId, conversation);
                if (newFacts.isEmpty()) {
                    log.debug("[Episodic] No facts extracted for user {}", userId);
                    return;
                }

                // 合并到已有事实列表
                List<EpisodicFact> existing = loadFacts(userId);
                List<EpisodicFact> merged = mergeFacts(existing, newFacts);

                // 限制总数
                if (merged.size() > properties.getEpisodicMaxFacts()) {
                    merged = merged.subList(merged.size() - properties.getEpisodicMaxFacts(), merged.size());
                }

                saveFacts(userId, merged);
                log.info("[Episodic] Recorded {} new facts for user {} (total: {})",
                        newFacts.size(), userId, merged.size());

            } catch (Exception e) {
                log.error("[Episodic] Failed to record facts for user {}", userId, e);
            }
        });
    }

    /**
     * 调用 LLM 从对话中提取结构化事实
     */
    private List<EpisodicFact> extractFacts(String userId, List<Message> conversation) {
        // 截取最近的对话
        List<Message> recent = conversation.size() > MAX_CONVERSATION_FOR_EXTRACT
                ? conversation.subList(conversation.size() - MAX_CONVERSATION_FOR_EXTRACT, conversation.size())
                : conversation;

        String conversationText = recent.stream()
                .map(msg -> msg.getMessageType().getValue().toUpperCase() + ": " + truncate(msg.getText(), 200))
                .collect(Collectors.joining("\n"));

        String prompt = extractPromptTemplate.replace("{conversation_history}", conversationText);

        // 使用 SimpleLlmCaller 调用 LLM
        UserConversationInfo user = new UserConversationInfo(userId, "system", "episodic", false);
        user.setAgent("memory");
        String result = llmCaller.call(user, new Prompt(prompt));

        return parseFacts(result, userId);
    }

    /**
     * 解析 LLM 返回的 JSON 数组为 EpisodicFact 列表
     */
    private List<EpisodicFact> parseFacts(String jsonResult, String sourceId) {
        if (jsonResult == null || jsonResult.isBlank()) return List.of();

        try {
            // 提取 JSON 数组（LLM 可能在 JSON 外包裹 markdown 代码块）
            String json = extractJsonArray(jsonResult);
            if (json == null) return List.of();

            JSONArray arr = JSONUtil.parseArray(json);
            List<EpisodicFact> facts = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String category = obj.getStr("category", "info");
                String content = obj.getStr("content", "");
                if (!content.isBlank()) {
                    facts.add(EpisodicFact.of(category, content.trim(), sourceId));
                }
            }
            return facts;
        } catch (Exception e) {
            log.warn("[Episodic] Failed to parse LLM output as facts: {}", truncate(jsonResult, 100), e);
            return List.of();
        }
    }

    /**
     * 从 LLM 输出中提取 JSON 数组
     */
    private String extractJsonArray(String text) {
        // 尝试直接解析
        text = text.trim();
        if (text.startsWith("[")) return text;

        // 尝试提取 ```json ... ``` 代码块
        int start = text.indexOf("[");
        int end = text.lastIndexOf("]");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    // ==================== retrieve ====================

    public String retrieve(String userId, Message query) {
        if (!properties.isEpisodicEnabled()) return null;

        List<EpisodicFact> facts = loadFacts(userId);
        if (facts.isEmpty()) return null;

        String queryText = query != null ? query.getText() : "";
        if (queryText == null) queryText = "";

        // 按相关性排序：关键词匹配 + 时间衰减
        List<EpisodicFact> relevant = rankByRelevance(facts, queryText);
        if (relevant.isEmpty()) return null;

        // 格式化为文本
        StringBuilder sb = new StringBuilder();
        sb.append("以下是关于该用户的历史记忆，请参考这些信息来辅助回答：\n");
        for (EpisodicFact fact : relevant) {
            sb.append("- ").append(fact.toDisplayString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 按相关性排序：简单的关键词匹配 + 时间衰减
     */
    private List<EpisodicFact> rankByRelevance(List<EpisodicFact> facts, String queryText) {
        if (queryText.isBlank()) {
            // 无查询文本时，返回最近的 N 条
            return facts.stream()
                    .sorted(Comparator.comparing(EpisodicFact::createdAt).reversed())
                    .limit(MAX_RETRIEVE_FACTS)
                    .toList();
        }

        // 提取查询中的关键词（简单分词：中文按2字切分，英文按空格）
        Set<String> keywords = extractKeywords(queryText);

        // 计算每条事实的相关性得分
        record ScoredFact(EpisodicFact fact, double score) {
        }
        List<ScoredFact> scored = facts.stream()
                .map(fact -> {
                    double score = computeRelevance(fact, keywords);
                    return new ScoredFact(fact, score);
                })
                .filter(sf -> sf.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredFact::score).reversed())
                .limit(MAX_RETRIEVE_FACTS)
                .toList();

        return scored.stream().map(ScoredFact::fact).toList();
    }

    /**
     * 计算事实与关键词的相关性得分
     */
    private double computeRelevance(EpisodicFact fact, Set<String> keywords) {
        String content = fact.content().toLowerCase();
        double score = 0;

        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase())) {
                score += 1.0;
            }
        }

        // 时间衰减：越近的事实得分越高（7天内加成20%，30天内加成10%）
        long daysSince = java.time.Duration.between(fact.createdAt(), Instant.now()).toDays();
        if (daysSince <= 7) {
            score *= 1.2;
        } else if (daysSince <= 30) {
            score *= 1.1;
        }

        // 偏好和决策类事实权重更高
        if ("preference".equals(fact.category()) || "decision".equals(fact.category())) {
            score *= 1.3;
        }

        return score;
    }

    /**
     * 简单的关键词提取
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        // 提取中文连续词（2字以上）
        String chinese = text.replaceAll("[^\\u4e00-\\u9fa5]", " ");
        for (String word : chinese.split("\\s+")) {
            if (word.length() >= 2) keywords.add(word);
        }
        // 提取英文单词
        String english = text.replaceAll("[\\u4e00-\\u9fa5]", " ");
        for (String word : english.split("\\s+")) {
            if (word.length() >= 3) keywords.add(word.toLowerCase());
        }
        return keywords;
    }

    // ==================== storage ====================

    public List<EpisodicFact> getAll(String userId) {
        return loadFacts(userId);
    }

    public void clear(String userId) {
        Path file = resolveFile(userId);
        try {
            Files.deleteIfExists(file);
            log.info("[Episodic] Cleared all facts for user {}", userId);
        } catch (IOException e) {
            log.error("[Episodic] Failed to clear facts for user {}", userId, e);
        }
    }

    private List<EpisodicFact> loadFacts(String userId) {
        Path file = resolveFile(userId);
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            YamlDocument doc = YamlParser.parse(Files.readString(file));
            String body = doc.body();
            if (body == null || body.isBlank()) return new ArrayList<>();

            JSONArray arr = JSONUtil.parseArray(body);
            List<EpisodicFact> facts = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                facts.add(new EpisodicFact(
                        obj.getStr("category", "info"),
                        obj.getStr("content", ""),
                        Instant.parse(obj.getStr("createdAt", Instant.now().toString())),
                        obj.getStr("sourceId", null)
                ));
            }
            return facts;
        } catch (Exception e) {
            log.error("[Episodic] Failed to load facts for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    private void saveFacts(String userId, List<EpisodicFact> facts) {
        Path file = resolveFile(userId);
        FileUtils.ensureDirectory(file.getParent());

        JSONArray arr = new JSONArray();
        for (EpisodicFact fact : facts) {
            JSONObject obj = new JSONObject();
            obj.set("category", fact.category());
            obj.set("content", fact.content());
            obj.set("createdAt", fact.createdAt().toString());
            obj.set("sourceId", fact.sourceId());
            arr.add(obj);
        }

        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("updatedAt", Instant.now().toString());
        frontmatter.put("factCount", String.valueOf(facts.size()));

        String content = YamlParser.serialize(new YamlDocument(frontmatter, arr.toString()));
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("[Episodic] Failed to save facts for user {}", userId, e);
        }
    }

    /**
     * 合并新旧事实，去重（基于 content 相似度）
     */
    private List<EpisodicFact> mergeFacts(List<EpisodicFact> existing, List<EpisodicFact> newFacts) {
        List<EpisodicFact> merged = new ArrayList<>(existing);
        for (EpisodicFact newFact : newFacts) {
            boolean duplicate = merged.stream()
                    .anyMatch(e -> e.content().equals(newFact.content())
                            || (e.category().equals(newFact.category())
                            && similarity(e.content(), newFact.content()) > 0.7));
            if (!duplicate) {
                merged.add(newFact);
            }
        }
        return merged;
    }

    /**
     * 简单的字符串相似度计算（Jaccard based on character bigrams）
     */
    private double similarity(String a, String b) {
        if (a == null || b == null) return 0;
        if (a.equals(b)) return 1.0;

        Set<String> bigramsA = bigrams(a);
        Set<String> bigramsB = bigrams(b);

        Set<String> intersection = new HashSet<>(bigramsA);
        intersection.retainAll(bigramsB);

        Set<String> union = new HashSet<>(bigramsA);
        union.addAll(bigramsB);

        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private Set<String> bigrams(String s) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            result.add(s.substring(i, i + 2));
        }
        return result;
    }

    private Path resolveFile(String userId) {
        return usersDir.resolve(userId).resolve("episodic-memory.yaml");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
