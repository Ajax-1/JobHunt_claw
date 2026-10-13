package com.git.hui.jobclaw.plugins.plannotebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.git.hui.jobclaw.core.agent.react.ReActMiddleware;
import com.git.hui.jobclaw.core.tools.AutoDiscoveredTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.IOException;

@AutoConfiguration
@ConditionalOnProperty(name = "agent.tools.plan-notebook.enabled", havingValue = "true")
public class PlanNotebookAutoConfiguration {

    @Bean
    public PlanNotebook planNotebook(
            @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
        return new PlanNotebook(workspace);
    }

    @Bean
    public PlanNotebookTool planNotebookTool(PlanNotebook notebook) {
        return new PlanNotebookTool(notebook);
    }

    @Bean
    public AutoDiscoveredTool<PlanNotebookTool> autoDiscoveredPlanNotebookTool(PlanNotebookTool tool) {
        return new AutoDiscoveredTool<>(tool);
    }

    @Bean
    public ReActMiddleware planHintReActMiddleware(PlanNotebook notebook) {
        return new PlanHintReActMiddleware(notebook);
    }
}
