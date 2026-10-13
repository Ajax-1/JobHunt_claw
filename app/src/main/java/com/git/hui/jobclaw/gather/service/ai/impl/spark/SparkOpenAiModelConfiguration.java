package com.git.hui.jobclaw.gather.service.ai.impl.spark;

import com.git.hui.jobclaw.gather.service.ai.impl.spark.config.SparkConfig;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通过OpenAI的接口方式，接入讯飞大模型
 *
 * @date 2025/8/26
 */
@Configuration
public class SparkOpenAiModelConfiguration {
    @Bean("sparkLiteModel")
    public ChatModel sparkChatModel(SparkConfig sparkConfig) {
        if (BooleanUtils.isTrue(sparkConfig.getOpenAiClient())) {
            // 使用OpenAI的接口方式实现讯飞模型的交互
            OpenAiApi openAiApi = OpenAiApi.builder().apiKey(sparkConfig.getApiKey())
                    .baseUrl(sparkConfig.getOpenAiBaseUrl())
                    .build();
            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder().model(sparkConfig.getChat().options().model()).build())
                    .build();
        } else {
            // 使用我们自定义实现的讯飞模型交互方式
            return new SparkLiteModel(sparkConfig);
        }
    }
}
