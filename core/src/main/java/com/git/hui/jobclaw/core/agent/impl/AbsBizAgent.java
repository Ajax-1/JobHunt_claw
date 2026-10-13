package com.git.hui.jobclaw.core.agent.impl;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.agent.llm.BizAgentLlmCaller;
import com.git.hui.jobclaw.core.configuration.event.PropertiesRefreshedEvent;
import com.git.hui.jobclaw.core.preference.AiUserPreferenceProperties;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.event.EventListener;

/**
 *
 * @date 2026/4/20
 */
public abstract class AbsBizAgent implements BizAgent {
    protected final ModelProviders modelProviders;
    protected final ChatMemory chatMemory;

    protected BizAgentLlmCaller llmCaller;

    protected IIdentityAgent identityAgent;

    public AbsBizAgent(ModelProviders modelProviders, ChatMemory chatMemory, IIdentityAgent identityAgent) {
        this.modelProviders = modelProviders;
        this.chatMemory = chatMemory;
        this.identityAgent = identityAgent;
    }

    @PostConstruct
    public void init() {
        this.llmCaller = new BizAgentLlmCaller(chatMemory, identityAgent, modelProviders, getSystemPrompt(), getTools());
    }


    public abstract String getSystemPrompt();

    public ToolCallback[] getTools() {
        return new ToolCallback[0];
    }


    @EventListener
    public void refreshLlmCache(PropertiesRefreshedEvent propertiesRefreshedEvent) {
        if (AiUserPreferenceProperties.class.equals(propertiesRefreshedEvent.getPropertiesClz())) {
            llmCaller.refreshCache();
        }
    }
}
