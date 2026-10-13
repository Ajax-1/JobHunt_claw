package com.git.hui.jobclaw.core.router.intent.impl;

import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 基于文件系统的会话状态管理器
 * 
 * AIDEV-NOTE: 会话状态存储在 workspace/sessions/{userId}/ 目录下
 * 
 * @date 2026/4/17
 */
@Slf4j
@Component
public class FileSystemSessionAgentBinder implements SessionAgentBinder {
    
    private static final Path SESSION_BASE = Path.of("workspace/sessions");
    private static final Duration DEFAULT_TTL = Duration.ofHours(6);
    
    public FileSystemSessionAgentBinder() {
        initDirectory();
    }
    
    private void initDirectory() {
        try {
            Files.createDirectories(SESSION_BASE);
        } catch (IOException e) {
            log.error("创建会话目录失败: {}", SESSION_BASE, e);
        }
    }
    
    @Override
    public void bind(String jobClawUserId, String sessionId, String agentId) {
        bind(jobClawUserId, sessionId, agentId, Instant.now().plus(DEFAULT_TTL));
    }
    
    @Override
    public void bind(String jobClawUserId, String sessionId, String agentId, Instant expiresAt) {
        Path sessionFile = getSessionFile(jobClawUserId, sessionId);
        SessionState state = loadOrCreate(sessionFile);
        
        state.boundAgent = new BoundAgentInfo(agentId, Instant.now(), expiresAt);
        
        save(sessionFile, state);
        log.debug("绑定会话 {}@{} -> {}", jobClawUserId, sessionId, agentId);
    }
    
    @Override
    public Optional<BoundAgentInfo> getBoundAgent(String jobClawUserId, String sessionId) {
        Path sessionFile = getSessionFile(jobClawUserId, sessionId);
        SessionState state = load(sessionFile);
        
        if (state == null || state.boundAgent == null) {
            return Optional.empty();
        }
        
        // 检查是否过期
        if (state.boundAgent.expiresAt().isBefore(Instant.now())) {
            log.debug("会话 {}@{} 绑定已过期", jobClawUserId, sessionId);
            return Optional.empty();
        }
        
        return Optional.of(state.boundAgent);
    }
    
    @Override
    public void unbind(String jobClawUserId, String sessionId) {
        Path sessionFile = getSessionFile(jobClawUserId, sessionId);
        SessionState state = load(sessionFile);
        
        if (state != null) {
            state.boundAgent = null;
            save(sessionFile, state);
            log.debug("解除会话 {}@{} 绑定", jobClawUserId, sessionId);
        }
    }
    
    @Override
    public boolean needsIntentRecognition(String jobClawUserId, String sessionId, String userMessage) {
        // 明确的重置指令 - 需要重新意图识别
        // AIDEV-NOTE: 这里只是判断是否需要重置，实际执行由 SystemCommandDispatcher 处理
        if (userMessage != null && userMessage.trim().toLowerCase().startsWith("/reset")) {
            return true;
        }
        
        // 不存在绑定
        if (getBoundAgent(jobClawUserId, sessionId).isEmpty()) {
            return true;
        }
        
        // 绑定已过期（getBoundAgent已经检查过期返回空）
        return getBoundAgent(jobClawUserId, sessionId).isEmpty();
    }
    
    @Override
    public List<IntentHistoryItem> getIntentHistory(String jobClawUserId, String sessionId) {
        Path sessionFile = getSessionFile(jobClawUserId, sessionId);
        SessionState state = load(sessionFile);
        
        if (state == null || state.intentHistory == null) {
            return Collections.emptyList();
        }
        
        return state.intentHistory;
    }
    
    @Override
    public void addIntentHistory(String jobClawUserId, String sessionId,
                                 PresetAgentIntro intentType, double confidence) {
        Path sessionFile = getSessionFile(jobClawUserId, sessionId);
        SessionState state = loadOrCreate(sessionFile);
        
        if (state.intentHistory == null) {
            state.intentHistory = new ArrayList<>();
        }
        
        state.intentHistory.add(new IntentHistoryItem(intentType, confidence, Instant.now()));
        
        // 保留最近10条历史
        if (state.intentHistory.size() > 10) {
            state.intentHistory = state.intentHistory.subList(
                    state.intentHistory.size() - 10, 
                    state.intentHistory.size());
        }
        
        save(sessionFile, state);
    }
    
    private Path getSessionFile(String jobClawUserId, String sessionId) {
        Path userDir = SESSION_BASE.resolve(jobClawUserId);
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            // 目录可能已存在，忽略
        }
        return userDir.resolve("session-" + sessionId + ".yaml");
    }
    
    private SessionState load(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        
        try {
            String content = Files.readString(file);
            YamlDocument doc = YamlParser.parse(content);
            Map<String, String> fm = doc.frontmatter();
            
            if (fm.isEmpty()) {
                return null;
            }
            
            SessionState state = new SessionState();
            state.sessionId = fm.get("sessionId");
            state.jobClawUserId = fm.get("jobClawUserId");
            
            // 解析绑定的Agent信息
            String boundAgentId = fm.get("boundAgentId");
            if (boundAgentId != null && !boundAgentId.isBlank()) {
                Instant boundAt = Instant.parse(fm.get("boundAt"));
                Instant expiresAt = Instant.parse(fm.get("expiresAt"));
                state.boundAgent = new BoundAgentInfo(boundAgentId, boundAt, expiresAt);
            }
            
            return state;
        } catch (IOException e) {
            log.error("读取会话状态失败: {}", file, e);
            return null;
        }
    }
    
    private SessionState loadOrCreate(Path file) {
        SessionState state = load(file);
        if (state == null) {
            state = new SessionState();
            // 从文件名提取sessionId
            String fileName = file.getFileName().toString();
            if (fileName.startsWith("session-")) {
                state.sessionId = fileName.substring(8, fileName.length() - 5);
            }
            // 从目录提取jobClawUserId
            state.jobClawUserId = file.getParent().getFileName().toString();
        }
        return state;
    }
    
    private void save(Path file, SessionState state) {
        try {
            Map<String, String> fm = new LinkedHashMap<>();
            fm.put("sessionId", state.sessionId);
            fm.put("jobClawUserId", state.jobClawUserId);
            
            if (state.boundAgent != null) {
                fm.put("boundAgentId", state.boundAgent.agentId());
                fm.put("boundAt", state.boundAgent.boundAt().toString());
                fm.put("expiresAt", state.boundAgent.expiresAt().toString());
            }
            
            String content = YamlParser.serialize(new YamlDocument(fm, null));
            
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
            Files.writeString(file, content, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("保存会话状态失败: {}", file, e);
        }
    }
    
    /**
     * 内存会话状态（简化实现）
     */
    private static class SessionState {
        String sessionId;
        String jobClawUserId;
        BoundAgentInfo boundAgent;
        List<IntentHistoryItem> intentHistory;
    }
}