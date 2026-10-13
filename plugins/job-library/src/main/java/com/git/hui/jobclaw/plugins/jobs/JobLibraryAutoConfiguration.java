package com.git.hui.jobclaw.plugins.jobs;

import com.git.hui.jobclaw.core.tools.AutoDiscoveredTool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class JobLibraryAutoConfiguration {

    @Bean
    public AutoDiscoveredTool<JobLibraryTool> autoDiscoveredJobLibraryTool(JobLibraryTool jobLibraryTool) {
        return new AutoDiscoveredTool<>(jobLibraryTool);
    }

    @Bean
    public JobLibraryTool jobLibraryTool() {
        return new JobLibraryTool();
    }
}
