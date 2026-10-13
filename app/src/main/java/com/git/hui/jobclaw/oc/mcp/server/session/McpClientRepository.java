package com.git.hui.jobclaw.oc.mcp.server.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * @date 2025/10/14
 */
public interface McpClientRepository extends JpaRepository<McpClientEntity, Long>, JpaSpecificationExecutor<McpClientEntity> {

    McpClientEntity findBySessionId(String sessionId);

    McpClientEntity findFirstByUserIdOrderByIdDesc(Long userId);
}
