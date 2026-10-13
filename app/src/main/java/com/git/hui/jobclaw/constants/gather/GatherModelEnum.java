package com.git.hui.jobclaw.constants.gather;

import com.git.hui.jobclaw.core.utils.json.StringBaseEnum;
import lombok.Getter;

/**
 * 数据采集任务状态
 *
 * @date 2025/7/14
 */
@Getter
public enum GatherModelEnum implements StringBaseEnum {
    DEEPSEEK("DeepSeek", "DeepSeek"),
    SPARK_LITE("SparkLite", "讯飞星火"),
    CHAT_GPT("ChatGPT", "ChatGPT"),
    ALI_BAILIAN("AliBaiLian", "阿里云百炼"),
    ;
    private final String value;
    private final String desc;

    GatherModelEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
