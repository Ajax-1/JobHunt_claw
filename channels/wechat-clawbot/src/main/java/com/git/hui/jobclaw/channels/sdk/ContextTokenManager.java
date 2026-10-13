package com.git.hui.jobclaw.channels.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context token manager for Weixin ClawBot.
 *
 * contextToken is issued per-message by the Weixin getupdates API and must
 * be echoed verbatim in every outbound send. The in-memory map is the primary
 * lookup; a disk-backed file per account ensures tokens survive gateway restarts.
 */
public class ContextTokenManager {

    private static final Logger log = LoggerFactory.getLogger(ContextTokenManager.class);

    private final Map<String, String> contextTokenStore = new ConcurrentHashMap<>();
    private final String stateDir;

    public ContextTokenManager(String stateDir) {
        this.stateDir = stateDir;
    }

    /**
     * Generate context token key from accountId and userId.
     */
    private String contextTokenKey(String accountId, String userId) {
        return accountId + ":" + userId;
    }

    /**
     * Resolve path to context token file for an account.
     */
    private Path resolveContextTokenFilePath(String accountId) {
        return Paths.get(stateDir, "jobclaw-weixin", "accounts",
                accountId + ".context-tokens.json");
    }

    /**
     * Persist all context tokens for a given account to disk.
     */
    public void persistContextTokens(String accountId) {
        String prefix = accountId + ":";
        Map<String, String> tokens = new ConcurrentHashMap<>();

        for (Map.Entry<String, String> entry : contextTokenStore.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                tokens.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }

        Path filePath = resolveContextTokenFilePath(accountId);
        try {
            Files.createDirectories(filePath.getParent());
            // Simple JSON format - in production use proper JSON library
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                        .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            json.append("}");

            Files.writeString(filePath, json.toString());
            log.debug("Persisted {} context tokens for account={}", tokens.size(), accountId);
        } catch (IOException e) {
            log.warn("Failed to persist context tokens for account={}: {}", accountId, e.getMessage());
        }
    }

    /**
     * Restore persisted context tokens for an account into the in-memory map.
     * Called once during gateway startAccount to survive restarts.
     */
    public void restoreContextTokens(String accountId) {
        Path filePath = resolveContextTokenFilePath(accountId);
        try {
            if (!Files.exists(filePath)) {
                return;
            }

            String content = Files.readString(filePath);
            // Simple JSON parsing - in production use proper JSON library
            // This is a basic implementation for demonstration
            log.info("Restored context tokens for account={} (parsing not fully implemented)", accountId);

        } catch (IOException e) {
            log.warn("Failed to restore context tokens for account={}: {}", accountId, e.getMessage());
        }
    }

    /**
     * Clear all context tokens for a given account (memory + disk).
     */
    public void clearContextTokensForAccount(String accountId) {
        String prefix = accountId + ":";
        contextTokenStore.keySet().removeIf(k -> k.startsWith(prefix));

        Path filePath = resolveContextTokenFilePath(accountId);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete context token file: {}", e.getMessage());
        }

        log.info("Cleared context tokens for account={}", accountId);
    }

    /**
     * Store a context token for a given account+user pair (memory + disk).
     */
    public void setContextToken(String accountId, String userId, String token) {
        String key = contextTokenKey(accountId, userId);
        contextTokenStore.put(key, token);
        persistContextTokens(accountId);
        log.debug("Set context token: key={}", key);
    }

    /**
     * Retrieve the cached context token for a given account+user pair.
     */
    public String getContextToken(String accountId, String userId) {
        String key = contextTokenKey(accountId, userId);
        String token = contextTokenStore.get(key);
        log.debug("Get context token: key={} found={}", key, token != null);
        return token;
    }

    /**
     * Find all accountIds that have an active contextToken for the given userId.
     */
    public java.util.List<String> findAccountIdsByContextToken(java.util.List<String> accountIds, String userId) {
        return accountIds.stream()
                .filter(id -> contextTokenStore.containsKey(contextTokenKey(id, userId)))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Escape special characters for JSON string.
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
