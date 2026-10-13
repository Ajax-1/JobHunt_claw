package com.git.hui.jobclaw.agents.identity.user;

import com.git.hui.jobclaw.agents.identity.init.InfoCollector;
import com.git.hui.jobclaw.agents.identity.user.collector.AiBasedIdentityCollector;
import com.git.hui.jobclaw.agents.identity.user.collector.RuleBasedIdentityCollector;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户画像收集
 * @date 2026/4/14
 */
@Component
public class IdentityCollectorSelector {
    private final Map<AiUserPreferenceProperties.CollectorType, InfoCollector> collectorMap;

    private final AiUserPreferenceProperties aiUserPreferenceProperties;


    public IdentityCollectorSelector(AiBasedIdentityCollector aiBasedIdentityCollector,
                                     RuleBasedIdentityCollector ruleBasedIdentityCollector,
                                     AiUserPreferenceProperties aiUserPreferenceProperties) {
        this.collectorMap = Map.of(AiUserPreferenceProperties.CollectorType.AI_BASED, aiBasedIdentityCollector,
                AiUserPreferenceProperties.CollectorType.RULE_BASED, ruleBasedIdentityCollector);
        this.aiUserPreferenceProperties = aiUserPreferenceProperties;
    }

    public InfoCollector getCollector(String jobClawUserId) {
        var tmp = aiUserPreferenceProperties.getUserPreference(jobClawUserId);
        if (tmp != null) {
            var collectorType = tmp.getCollector();
            if (collectorType == null) {
                collectorType = AiUserPreferenceProperties.CollectorType.AI_BASED;
            }
            return collectorMap.get(collectorType);
        } else {
            return collectorMap.get(AiUserPreferenceProperties.CollectorType.AI_BASED);
        }
    }
}
