package com.git.hui.jobclaw.agents.jobfetch.extract.impl;

import com.git.hui.jobclaw.agents.jobfetch.extract.AbsJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 基于AI的通用职位信息提取器
 * 使用大模型从各种文本内容中提取结构化的职位信息
 *
 * @date 2026/4/18
 */
@Slf4j
@Component
public class TextJobExtractor extends AbsJobExtractor {

    public TextJobExtractor(JobLlmCaller jobLlmCaller,
                            @Value("classpath:prompts/job-info-extraction-prompt.md")
                            Resource promptResource) {
        super(jobLlmCaller, promptResource);
    }


    @Override
    public String getName() {
        return "JobExtractorFromText";
    }

    @Override
    public boolean supports(String contentType) {
        // AI提取器支持所有文本类型的内容
        return contentType == null
                || contentType.startsWith("text/")
                || contentType.contains("html")
                || contentType.contains("markdown");
    }


}
