package com.git.hui.jobclaw.agents.jobfetch.extract.impl;

import cn.hutool.core.io.IoUtil;
import com.git.hui.jobclaw.agents.jobfetch.extract.AbsJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import com.git.hui.jobclaw.core.utils.files.ChannelStorageHelper;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * 基于AI的通用职位信息提取器
 * 使用大模型从各种文本内容中提取结构化的职位信息
 *
 * @date 2026/4/18
 */
@Slf4j
@Component
public class TextFileJobExtractor extends AbsJobExtractor {

    private final ChannelStorageHelper channelStorageHelper;

    public TextFileJobExtractor(JobLlmCaller jobLlmCaller,
                                @Value("classpath:/prompts/job-info-extraction-prompt.md")
                                Resource promptResource,
                                ChannelStorageHelper channelStorageHelper) {
        super(jobLlmCaller, promptResource);
        this.channelStorageHelper = channelStorageHelper;
    }

    @Override
    public String getName() {
        return "JobExtractorFromFile";
    }


    @Override
    protected UserMessage buildUserMessage(ChannelReceiveMessage message) {
        var file = loadTmpFile(message.getFiles().get(0));
        String content = new String(file.bytes);

        UserMessage.Builder builder = UserMessage.builder()
                .text(new PromptTemplate("{text}\n{format}")
                        .render(Map.of("text", content, "format", gatherResConverter.getFormat()))
                );

        return builder.build();
    }


    public GatherFileBo loadTmpFile(ChannelReceiveMessage.FileMsg file) {
        try {
            InputStream inputStream = channelStorageHelper.loadFile(file.getFilePath().toString());
            byte[] bytes = IoUtil.readBytes(inputStream);
            return new GatherFileBo(bytes, file.getMimeType());
        } catch (Exception e) {
            log.error("加载临时文件失败: {}", file, e);
            throw e;
        }
    }

    @Override
    public boolean supports(String contentType) {
        // AI提取器支持所有文本类型的内容
        return contentType == null
                || contentType.startsWith("text/")
                || contentType.contains("html")
                || contentType.contains("markdown")
                ;
    }

    public record GatherFileBo(byte[] bytes, String contentType) {
    }

}
