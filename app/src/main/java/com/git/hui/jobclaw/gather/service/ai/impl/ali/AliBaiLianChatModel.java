//package com.git.hui.jobclaw.gather.service.ai.impl.ali;
//
//import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
//import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
//import com.git.hui.jobclaw.gather.service.ai.impl.AbsOcChatModelApi;
//import io.modelcontextprotocol.client.McpAsyncClient;
//import jakarta.annotation.PostConstruct;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.chat.prompt.ChatOptions;
//import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.util.Collections;
//import java.util.List;
//
///**
// * 阿里云百炼模型
// *
// * @date 2025/8/25
// */
////@Component
//public class AliBaiLianChatModel extends AbsOcChatModelApi {
//    private final ChatModel chatModel;
//
//    private final ChatClient chatClient;
//    private ChatClient imgClient;
//
//    /**
//     * 多模态的图片理解模型
//     */
//    @Value("${spring.ai.dashscope.multi-model:qwen-omni-turbo}")
//    private String sigImgModel;
//
//    public AliBaiLianChatModel(DashScopeChatModel chatModel, List<McpAsyncClient> mcpClients) {
//        this.chatModel = chatModel;
//
//        chatClient = ChatClient.builder(chatModel)
//                .defaultSystem(GATHER_SYSTEM_PROMPT)
//                .defaultAdvisors(new SimpleLoggerAdvisor())
//                // 将MCP Client 注册为工具
//                .defaultToolCallbacks(new AsyncMcpToolCallbackProvider(mcpClients))
//                .build();
//    }
//
//    @PostConstruct
//    public void postInitImgClint() {
//        // 图片理解
//        imgClient = ChatClient.builder(chatModel)
//                .defaultSystem(GATHER_SYSTEM_PROMPT)
//                .defaultOptions(ChatOptions.builder()
//                        .model(sigImgModel)
//                        .stopSequences(Collections.emptyList()).build())
//                .defaultAdvisors(new SimpleLoggerAdvisor())
//                .build();
//    }
//
//    @Override
//    public GatherModelEnum modelEnum() {
//        return GatherModelEnum.ALI_BAILIAN;
//    }
//
//
//    @Override
//    public ChatClient chatClient() {
//        return chatClient;
//    }
//
//    @Override
//    public ChatClient imgClient() {
//        return imgClient;
//    }
//
//    @Override
//    public ChatModel chatModel() {
//        return chatModel;
//    }
//
//    @Override
//    public String chatModelName() {
//        return chatModel.getDefaultOptions().getModel();
//    }
//
//    @Override
//    public String imgModelName() {
//        return sigImgModel;
//    }
//}
