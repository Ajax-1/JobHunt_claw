package com.git.hui.jobclaw.agents.jobfetch.crawler.impl;

import com.git.hui.jobclaw.agents.jobfetch.crawler.JobCrawler;
import com.git.hui.jobclaw.agents.jobfetch.crawler.impl.tool.SmartWebFetchTool;
import com.git.hui.jobclaw.agents.jobfetch.llm.JobLlmCaller;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.agents.jobfetch.util.GatherResFormat;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 从提供职位列表的聚合类网站爬去相关信息
 * 使用SmartWebFetchTool抓取网页内容,然后提取职位信息
 * 支持分页处理,当返回内容较多时会进行多轮对话获取完整数据
 *
 * @date 2026/4/18
 */
@Slf4j
@Component
public class AggregateWebCrawler implements JobCrawler {
    private static final int MAX_CHAT_CNT = 20;

    protected final JobLlmCaller jobLlmCaller;

    protected BeanOutputConverter<ArrayList<FetchedJobInfo>> gatherResConverter;
    protected final Resource promptResource;

    public AggregateWebCrawler(JobLlmCaller jobLlmCaller,
                               @Value("classpath:prompts/job-info-crawler-prompt.md")
                               Resource promptResource) {
        this.jobLlmCaller = jobLlmCaller;
        this.promptResource = promptResource;
        gatherResConverter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public String getName() {
        return "AggregateWebCrawler";
    }

    @Override
    public boolean supports(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            // 支持常见的招聘网站
            return host != null && (
                    host.contains("zhaopin.com") ||      // 智联招聘
                            host.contains("51job.com") ||         // 前程无忧
                            host.contains("lagou.com") ||         // 拉勾网
                            host.contains("bosszhipin.com") ||    // BOSS直聘
                            host.contains("liepin.com") ||        // 猎聘
                            host.contains("shixiseng.com") ||     // 实习僧
                            host.endsWith(".com") ||              // 其他.com网站
                            host.endsWith(".cn")                  // 其他.cn网站
            );
        } catch (Exception e) {
            log.warn("无效的URL: {}", url);
            return false;
        }
    }

    @Override
    public List<FetchedJobInfo> crawl(UserConversationInfo userConversationInfo, String url, String originMsg) {
        log.info("开始爬取URL! {} -> {}", originMsg, url);

        try {
            SmartWebFetchTool webFetchTool = SmartWebFetchTool.builder(jobLlmCaller.simpleClient(userConversationInfo))
                    // fixme 为了安全起见，不应该关闭这个安全校验
                    .domainSafetyCheck(false)
                    .summaryEnable(false)
                    .build();
            var tools = ToolCallbacks.from(webFetchTool);

            // 创建 memory 实例，保存上下文
            ChatMemory chatMemory = MessageWindowChatMemory
                    .builder()
                    .maxMessages(MAX_CHAT_CNT)
                    .build();
            chatMemory.add(userConversationInfo.conversationId(), new SystemMessage(promptResource));

            String userMessage = String.format("%s\n网页地址：%s\n%s", originMsg, url, gatherResConverter.getFormat());

            // 执行分页提取
            List<String> itemList = extractByPage(userConversationInfo, chatMemory, userMessage, tools, userConversationInfo.conversationId());

            if (itemList.isEmpty()) {
                log.info("未从网页中提取到职位信息: {}", url);
                return List.of();
            }

            // 转换为JobInfo对象
            List<FetchedJobInfo> jobInfos = itemList.stream()
                    .map(this::toJobInfo)
                    .filter(job -> job != null && job.isValid())
                    .toList();

            log.info("从网页{}中成功提取{}条职位信息", url, jobInfos.size());
            return jobInfos;

        } catch (Exception e) {
            log.error("爬取网页失败: {}", url, e);
            throw new RuntimeException("爬取网页失败: " + url, e);
        }
    }

    /**
     * 分页提取职位信息
     * 当大模型返回内容不完整时,进行多轮对话获取剩余内容
     * 采用明确的行号范围指定方式,确保精确提取
     *
     * @param chatMemory     聊天内存
     * @param userMessage    用户消息(包含网页内容)
     * @param user          用户信息
     * @param tools          工具回调数组
     * @param conversationId 会话ID
     * @return 提取的原始JSON字符串列表
     */
    private List<String> extractByPage(
            UserConversationInfo user,
            ChatMemory chatMemory,
            String userMessage,
            ToolCallback[] tools,
            String conversationId) {
        List<String> itemList = new ArrayList<>();
        StringBuilder remain = new StringBuilder();
        int cnt = 0;
        int extractedCount = 0; // 已提取的职位数量

        // 工具配置
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .build();

        while (true) {
            log.info("{}#第{}次大模型数据解析", conversationId, cnt + 1);

            UserMessage msg;
            if (cnt == 0) {
                // 首次请求:发送完整用户消息
                msg = new UserMessage(userMessage);
            } else {
                // 后续请求:明确指定继续提取的位置和数量
                String continuePrompt = buildContinuePrompt(extractedCount, cnt);
                msg = new UserMessage(continuePrompt);
                log.info("{}#继续提取提示: {}", conversationId, continuePrompt);
            }

            chatMemory.add(conversationId, msg);

            try {
                Prompt query = Prompt.builder()
                        .messages(chatMemory.get(conversationId))
                        .chatOptions(chatOptions)
                        .build();

                if (log.isDebugEnabled()) {
                    log.debug("{}#req: {}", conversationId, StringUtils.replaceChars(query.toString(), "\n", ""));
                }

                ChatResponse response = jobLlmCaller.response(user, query);
                AssistantMessage assistantMessage = response.getResult().getOutput();

                if (log.isDebugEnabled()) {
                    log.debug("{}#res: {}", conversationId, StringUtils.replaceChars(assistantMessage.toString(), "\n", ""));
                }

                cnt += 1;

                String outText = assistantMessage.getText().trim();
                List<String> currentBatch = GatherResFormat.extract(remain, outText);

                // 统计本次提取的数量
                int batchCount = currentBatch.size();
                extractedCount += batchCount;
                itemList.addAll(currentBatch);

                log.info("{}#本轮提取{}条职位,累计{}条", conversationId, batchCount, extractedCount);

                // 判断是否完成提取
                boolean isComplete = checkExtractionComplete(outText, batchCount, cnt);
                if (isComplete) {
                    log.info("{}#经过{}轮对话,共提取{}条职位,完成大模型调用", conversationId, cnt, extractedCount);
                    break;
                }

            } catch (Exception e) {
                log.error("{}#gather error", conversationId, e);
                break;
            }
        }

        return itemList;
    }

    /**
     * 构建继续提取的提示词
     * 明确告诉大模型从哪个位置继续提取
     *
     * @param extractedCount 已提取的职位数量
     * @param roundCount     当前轮次
     * @return 提示词
     */
    private String buildContinuePrompt(int extractedCount, int roundCount) {
        if (extractedCount == 0) {
            // 如果上一轮没有提取到任何职位,可能是格式问题,要求重新检查
            return "上一轮未提取到有效职位信息。请重新仔细检查网页内容,按照分页提取规则,提取接下来的职位信息。";
        }

        // 明确告知已提取数量,要求继续提取剩余内容
        return String.format(
                "已成功提取%d个职位。请继续从网页中提取剩余的职位信息。\n" +
                        "要求:\n" +
                        "1. 不要重复提取已经返回的%d个职位\n" +
                        "2. 仔细查找网页中还未提取的职位\n" +
                        "3. 如果还有职位,请继续以JSON数组格式返回\n" +
                        "4. 如果已经没有更多职位,请返回空数组 []\n" +
                        "5. 在JSON后面添加注释说明是否还有更多职位",
                extractedCount, extractedCount
        );
    }

    /**
     * 检查提取是否完成
     *
     * @param outText      大模型输出文本
     * @param batchCount   本轮提取数量
     * @param roundCount   当前轮次
     * @return true表示完成, false表示继续
     */
    private boolean checkExtractionComplete(String outText, int batchCount, int roundCount) {
        // 1. 达到最大轮次限制
        if (roundCount >= MAX_CHAT_CNT) {
            log.warn("达到最大对话轮次限制({}),强制结束", MAX_CHAT_CNT);
            return true;
        }

        // 2. 本轮没有提取到任何职位,说明可能已经提取完毕
        if (batchCount == 0) {
            log.info("本轮未提取到新职位,判断为提取完毕");
            return true;
        }

        // 3. 检测完整性标记
        if (outText.contains("// 已提取完毕") || outText.contains("//已提取完毕")) {
            log.info("检测到大模型返回'已提取完毕'标记");
            return true;
        }

        // 4. JSON数组正常结束
        if (outText.endsWith("]") || outText.endsWith("```") || outText.endsWith("]")) {
            // 检查是否还有"还有更多"的标记
            if (outText.contains("// 还有更多") || outText.contains("//还有更多")) {
                log.info("检测到'还有更多职位'标记,继续提取");
                return false;
            }
            // 如果没有明确标记,但JSON完整且本轮有数据,再尝试一轮确认
            if (roundCount < 3) {
                log.info("JSON完整但未检测到明确标记,再尝试一轮确认");
                return false;
            }
            return true;
        }

        // 5. 检测大模型是否重新开始返回完整数据
        if (roundCount > 1 && outText.startsWith("```json")) {
            int jsonBeginIndex = outText.indexOf("[");
            if (jsonBeginIndex > 0 && jsonBeginIndex < 15) {
                log.info("大模型重复返回完整解析数据,主动退出多轮对话");
                return true;
            }
        }

        // 默认继续提取
        return false;
    }


    /**
     * 将JSON字符串转换为JobInfo对象
     */
    private FetchedJobInfo toJobInfo(String jsonStr) {
        try {
            return JsonUtil.toObj(jsonStr, FetchedJobInfo.class);
        } catch (Exception e) {
            log.warn("解析职位信息失败: {}", jsonStr, e);
            return null;
        }
    }
}
