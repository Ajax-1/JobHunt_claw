package com.git.hui.jobclaw.agents.identity;

import com.git.hui.jobclaw.agents.identity.global.AgentIdentityManager;
import com.git.hui.jobclaw.agents.identity.info.UserAgentInfoManager;
import com.git.hui.jobclaw.agents.identity.init.UnifiedIdentityInitializer;
import com.git.hui.jobclaw.agents.identity.soul.UserAgentSoulManager;
import com.git.hui.jobclaw.agents.identity.user.UserIdentityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @date 2026/4/17
 */
@Configuration
public class IdentityConfiguration {

    @Bean
    public IdentityAgent identityAgent(AgentIdentityManager agentIdentityManager,
                                       UserAgentSoulManager agentSoulManager,
                                       UserAgentInfoManager agentInfoManager,
                                       UserIdentityManager userIdentityManager,
                                       UnifiedIdentityInitializer unifiedInitializer
    ) {
        return new IdentityAgent(agentIdentityManager, agentSoulManager, agentInfoManager, userIdentityManager, unifiedInitializer);
    }
}
