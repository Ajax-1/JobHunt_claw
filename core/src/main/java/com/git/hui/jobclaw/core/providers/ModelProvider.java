package com.git.hui.jobclaw.core.providers;

import org.springframework.ai.model.Model;

/**
 *
 * @date 2026/4/9
 */
public interface ModelProvider {
    /**
     * 模型接入的API风格
     *
     * @return
     */
    String apiStyle();

    /**
     * 构建模型
     *
     * @param info
     * @return
     */
    Model model(ModelConfig.ModelInfo info);
}
