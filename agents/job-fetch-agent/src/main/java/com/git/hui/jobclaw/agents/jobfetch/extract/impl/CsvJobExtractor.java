package com.git.hui.jobclaw.agents.jobfetch.extract.impl;

import cn.hutool.core.io.IoUtil;
import com.git.hui.jobclaw.agents.jobfetch.extract.AbsJobExtractor;
import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.utils.files.ChannelStorageHelper;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * csv文件解析
 * @date 2026/4/20
 */
@Slf4j
@Component
public class CsvJobExtractor extends AbsJobExtractor {
    private final ChannelStorageHelper channelStorageHelper;
    private final int pageSize= 8;

    public CsvJobExtractor(JobLlmCaller jobLlmCaller,
                           @Value("classpath:/prompts/job-info-extraction-prompt.md")
                           Resource promptResource,
                           ChannelStorageHelper channelStorageHelper) {
        super(jobLlmCaller, promptResource);
        this.channelStorageHelper = channelStorageHelper;
    }

    @Override
    public List<FetchedJobInfo> extractFromInput(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        // 获取文件内容
        Pair<String, List<String>> pair = parseContentsFromCsv(loadTmpFile(message.getFiles().get(0)));

        // 对内容进行拆分，避免上下文过长导致解析异常
        List<String> contents = pair.getSecond();
        // 每五个作为一批
        List<List<String>> contentsList = new ArrayList<>();
        for (int i = 0; i < contents.size(); i += pageSize) {
            contentsList.add(contents.subList(i, Math.min(i + pageSize, contents.size())));
        }

        List<FetchedJobInfo> res = new ArrayList<>();
        for (List<String> sub : contentsList) {
            String builder = pair.getFirst() + "\n" + Joiner.on("\n").join(sub);
            var msgBuilder = ChannelReceiveMessage.builder()
                    .msgId(message.getMsgId())
                    .fromUserId(message.getFromUserId())
                    .jobClawUserId(message.getJobClawUserId())
                    .passThrough(message.getPassThrough())
                    .channel(message.getChannel())
                    .stream(message.isStream())
                    .message(builder);
            var subList = super.extractFromInput(userConversationInfo, msgBuilder.build());
            res.addAll(subList);
        }

        return res;
    }

    public TextFileJobExtractor.GatherFileBo loadTmpFile(ChannelReceiveMessage.FileMsg file) {
        try {
            InputStream inputStream = channelStorageHelper.loadFile(file.getFilePath().toString());
            byte[] bytes = IoUtil.readBytes(inputStream);
            return new TextFileJobExtractor.GatherFileBo(bytes, file.getMimeType());
        } catch (Exception e) {
            log.error("加载临时文件失败: {}", file, e);
            throw e;
        }
    }

    private Pair<String, List<String>> parseContentsFromCsv(TextFileJobExtractor.GatherFileBo file) {
        try {
            byte[] bytes;
            if (file == null) {
                // 使用默认的图片进行兜底
                Resource resource = new ClassPathResource("data/oc.csv");
                bytes = resource.getContentAsByteArray();
            } else {
                bytes = file.bytes();
            }

            // 读取数据，进行拆分，用于多次与大模型交互；避免大模型返回结果截断
            String datas = new String(bytes, "utf-8");
            String[] lines = org.apache.commons.lang3.StringUtils.splitByWholeSeparator(datas, "\n");
            String title = lines[0];
            List<String> contents = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                contents.add(lines[i]);
            }
            return Pair.of(title, contents);
        } catch (IOException e) {
            log.error("解析文件失败: {}", file, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "jobExtractorFromCsv";
    }

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.contains("csv");
    }
}
