package com.git.hui.jobclaw.agents.identity.global;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages global agent operation manual (agent.md).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read agent manual from workspace/agent.md</li>
 *   <li>Validate format of agent manual</li>
 *   <li>Handle missing files gracefully</li>
 *   <li>Do NOT provide save method - only manual editing supported</li>
 * </ul>
 *
 * AIDEV-NOTE: Global agent.md manager for Phase 1 implementation
 */
@Component
public class AgentIdentityManager {

    private static final Logger log = LoggerFactory.getLogger(AgentIdentityManager.class);

    private final Path agentManualPath;

    public AgentIdentityManager(@Value("${agent.workspace:Unknown}") Resource workspaceDir,
                                @Value("${agent.identity.path:AGENT.md}") String agentManualPath) throws IOException {
        this.agentManualPath = workspaceDir.getFile().toPath().resolve(agentManualPath);
        log.info("AgentIdentityManager initialized with path: {}", this.agentManualPath);
    }

    /**
     * Load agent manual from file.
     *
     * @return agent manual markdown content, or empty string if not exists
     */
    public String loadAgentIdentity() {
        if (!Files.exists(agentManualPath)) {
            log.debug("No agent manual found at: {}", agentManualPath);
            return "";
        }

        try {
            String content = Files.readString(agentManualPath);
            log.debug("Loaded agent manual ({} chars)", content.length());
            return content;
        } catch (IOException e) {
            log.error("Failed to load agent manual from: {}", agentManualPath, e);
            return "";
        }
    }
}
