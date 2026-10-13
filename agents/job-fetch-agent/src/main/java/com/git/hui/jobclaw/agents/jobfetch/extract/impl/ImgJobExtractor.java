package com.git.hui.jobclaw.agents.jobfetch.extract.impl;

import com.git.hui.jobclaw.agents.jobfetch.extract.AbsJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 *
 * @date 2026/4/18
 */
@Slf4j
@Component
public class ImgJobExtractor extends AbsJobExtractor {

    public ImgJobExtractor(JobLlmCaller jobLlmCaller,
                           @Value("classpath:prompts/job-info-extraction-prompt.md")
                           Resource promptResource) {
        super(jobLlmCaller, promptResource);
    }


    @Override
    public String getName() {
        return "JobExtractorFromImage";
    }

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }
}
