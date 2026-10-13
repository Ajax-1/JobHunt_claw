package com.git.hui.jobclaw.agents.jobfetch.extract;

import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.agents.jobfetch.util.GatherResFormat;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 * @date 2026/4/18
 */
@Slf4j
public abstract class AbsJobExtractor implements JobExtractor {
    protected static final int MAX_CHAT_CNT = 20;
    protected final JobLlmCaller jobLlmCaller;

    protected final Resource promptResource;

    protected BeanOutputConverter<ArrayList<FetchedJobInfo>> gatherResConverter;

    public AbsJobExtractor(JobLlmCaller jobLlmCaller,
                           Resource promptResource) {
        this.jobLlmCaller = jobLlmCaller;
        this.promptResource = promptResource;
        gatherResConverter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });
    }

    /**
     * 针对大模型响应结果截断的场景，进行多轮对话，尝试获取完整的返回
     * 实现原理：基于 chatModel, 借助 ChatMemory 自动实现多轮对话，
     */
    @Override
    public List<FetchedJobInfo> extractFromInput(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
        // 创建 memory 实例，保存上下文
        ChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .maxMessages(MAX_CHAT_CNT)
                .build();
        String conversationId = userConversationInfo.conversationId();

        SystemMessage systemMessage = new SystemMessage(promptResource);
        chatMemory.add(conversationId, systemMessage);

        var initUserMsg = buildUserMessage(message);
        var itemList = extractByPage(userConversationInfo, initUserMsg, chatMemory, conversationId);

        if (CollectionUtils.isEmpty(itemList)) {
            return List.of();
        }

        return itemList.stream().map(this::toBo).filter(Objects::nonNull).collect(Collectors.toList());
    }

    protected List<String> extractByPage(UserConversationInfo userConversationInfo,
                                         UserMessage initUserMsg,
                                         ChatMemory chatMemory,
                                         String conversationId) {
        List<String> itemList = new ArrayList<>();
        StringBuilder remain = new StringBuilder();
        int cnt = 0;
        while (true) {
            log.info("{}#第{}次大模型数据解析", conversationId, cnt + 1);
            UserMessage msg;
            if (cnt == 0) {
                msg = initUserMsg;
            } else {
                msg = new UserMessage("你之前返回的结果不完整，继续返回剩余的内容");
            }

            chatMemory.add(conversationId, msg);

            try {
                Prompt query = new Prompt(chatMemory.get(conversationId));
                if (log.isDebugEnabled()) {
                    // 一行显示日志
                    log.debug("{}#req: {}", conversationId, StringUtils.replaceChars(query.toString(), "\n", ""));
                }
                ChatResponse response = jobLlmCaller.response(userConversationInfo, query);
                AssistantMessage assistantMessage = response.getResult().getOutput();
                if (log.isDebugEnabled()) {
                    // 一行显示和日志
                    log.debug("{}#res: {}", conversationId, StringUtils.replaceChars(assistantMessage.toString(), "\n", ""));
                }

                chatMemory.add(conversationId, assistantMessage);
                cnt += 1;

                String outText = assistantMessage.getText().trim();
                itemList.addAll(GatherResFormat.extract(remain, outText));
                if (outText.endsWith("```") || outText.endsWith("]") || cnt >= MAX_CHAT_CNT) {
                    // 做一个次数限制，避免死循环的调用大模型
                    log.info("{}#经过{}论对话，完成大模型调用", conversationId, cnt);
                    break;
                }
                if (cnt > 1 && outText.startsWith("```json")) {
                    int jsonBeginIndex = outText.indexOf("[");
                    if (jsonBeginIndex > 0 && jsonBeginIndex < 15) {
                        // 表示大模型又重新返回了完整的数据，为了避免大模型总是重复解析，我们直接退出循环
                        log.info("{}#大模型重复返回完整解析数据，主动退出多轮对话", conversationId);
                        break;
                    }
                }
            } catch (Exception e) {
                // 避免因为多次调用模型出现异常，导致前面获取的数据被丢掉，我们直接跳出来，将已经解析的结果保存下来
                log.error("{}#gather error", conversationId, e);
                break;
            }
        }
        return itemList;
    }

    protected UserMessage buildUserMessage(ChannelReceiveMessage message) {
        UserMessage.Builder builder = UserMessage.builder()
                .text(new PromptTemplate("{text}\n{format}").render(Map.of("text", message.getMessage(), "format", gatherResConverter.getFormat()))
                );
        if (!CollectionUtils.isEmpty(message.getMedias())) {
            var img = message.getMedias().get(0);
            var mime = img.getMimeType() != null ? MimeType.valueOf(img.getMimeType()) : MimeTypeUtils.IMAGE_PNG;
            builder.media(new Media(mime, new FileSystemResource(img.getFilePath())));
        }
        return builder.build();
    }


    private FetchedJobInfo toBo(String item) {
        try {
            return JsonUtil.toObj(item, FetchedJobInfo.class);
        } catch (Exception e) {
            log.warn("解析异常: {}", item, e);
        }
        return null;
    }
}
