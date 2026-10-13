-- 持久化内置 LLM Provider 元数据；API Key 在后台 LLM 供应商页面保存。
DELETE FROM `global_env_config` WHERE `config_key` LIKE 'agent.ai.providers.zhipufree.%';
-- 智谱供应商已下线：清理存量库中的 zhipu 供应商配置
DELETE FROM `global_env_config` WHERE `config_key` LIKE 'agent.ai.providers.zhipu.%';
-- 存量默认偏好若仍指向智谱，统一改写为 DeepSeek V4.1 Flash
UPDATE `global_env_config` SET `config_value` = 'deepseek#deepseek-flash'
 WHERE `config_key` IN ('agent.ai.preference[0].models.text', 'agent.ai.preference[0].models.vision')
   AND `config_value` LIKE 'zhipu#%';
-- DeepSeek V4.1 Flash 更名 + 多模态：uk_config_key 命中重复时只更新 key 本身，需先清理旧值行才能生效
DELETE FROM `global_env_config` WHERE `config_key` = 'agent.ai.providers.deepseek.models[0].name' AND `config_value` = 'deepseek-v4-flash';
DELETE FROM `global_env_config` WHERE `config_key` = 'agent.ai.providers.deepseek.models[0].multimodal' AND `config_value` = 'false';

INSERT INTO `global_env_config` (`config_key`, `config_value`, `config_desc`, `config_type`, `enabled`, `priority`) VALUES
('agent.ai.providers.silicon.api-style', 'openai', '硅基流动 API 风格', 'string', 1, 100),
('agent.ai.providers.silicon.base-url', 'https://api.siliconflow.cn', '硅基流动 Base URL', 'string', 1, 100),
('agent.ai.providers.silicon.completions-path', '/v1/chat/completions', '硅基流动对话路径', 'string', 1, 100),
('agent.ai.providers.silicon.embeddings-path', '/v1/embeddings', '硅基流动嵌入路径', 'string', 1, 100),
('agent.ai.providers.silicon.images-path', '/v1/images/embeddings', '硅基流动图片路径', 'string', 1, 100),
('agent.ai.providers.silicon.models[0].name', 'Qwen/Qwen2.5-7B-Instruct', '硅基流动文本模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[0].type', 'TEXT', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[0].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[1].name', 'Qwen/Qwen3.5-4B', '硅基流动文本模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[1].type', 'TEXT', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[1].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[2].name', 'Qwen/Qwen3-8B', '硅基流动文本模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[2].type', 'TEXT', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[2].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[3].name', 'PaddlePaddle/PaddleOCR-VL-1.5', '硅基流动视觉模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[3].type', 'VISION', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[3].multimodal', 'true', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[4].name', 'deepseek-ai/DeepSeek-OCR', '硅基流动视觉模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[4].type', 'VISION', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[4].multimodal', 'true', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[5].name', 'Kwai-Kolors/Kolors', '硅基流动生图模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[5].type', 'IMAGE', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[5].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[6].name', 'TeleAI/TeleSpeechASR', '硅基流动语音识别模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[6].type', 'ASR', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[6].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[7].name', 'FunAudioLLM/SenseVoiceSmall', '硅基流动语音识别模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[7].type', 'ASR', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[7].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[8].name', 'BAAI/bge-m3', '硅基流动嵌入模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[8].type', 'EMBEDDING', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[8].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[9].name', 'netease-youdao/bce-embedding-base_v1', '硅基流动嵌入模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[9].type', 'EMBEDDING', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[9].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.silicon.models[10].name', 'BAAI/bge-large-zh-v1.5', '硅基流动嵌入模型', 'string', 1, 100),
('agent.ai.providers.silicon.models[10].type', 'EMBEDDING', '硅基流动模型类型', 'string', 1, 100),
('agent.ai.providers.silicon.models[10].multimodal', 'false', '硅基流动模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.deepseek.api-style', 'openai', 'DeepSeek API 风格', 'string', 1, 100),
('agent.ai.providers.deepseek.base-url', 'https://api.deepseek.com', 'DeepSeek Base URL', 'string', 1, 100),
('agent.ai.providers.deepseek.completions-path', '/chat/completions', 'DeepSeek 对话路径', 'string', 1, 100),
('agent.ai.providers.deepseek.models[0].name', 'deepseek-flash', 'DeepSeek V4.1 Flash 模型', 'string', 1, 100),
('agent.ai.providers.deepseek.models[0].type', 'TEXT', 'DeepSeek 模型类型', 'string', 1, 100),
('agent.ai.providers.deepseek.models[0].multimodal', 'true', 'DeepSeek 模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.deepseek.models[1].name', 'deepseek-v4-pro', 'DeepSeek 文本模型', 'string', 1, 100),
('agent.ai.providers.deepseek.models[1].type', 'TEXT', 'DeepSeek 模型类型', 'string', 1, 100),
('agent.ai.providers.deepseek.models[1].multimodal', 'false', 'DeepSeek 模型是否多模态', 'boolean', 1, 100),
('agent.ai.providers.step.api-style', 'openai', 'Step API 风格', 'string', 1, 100),
('agent.ai.providers.step.base-url', 'https://api.stepfun.com/step_plan/v1', 'Step Base URL', 'string', 1, 100),
('agent.ai.providers.step.completions-path', '/chat/completions', 'Step 对话路径', 'string', 1, 100),
('agent.ai.providers.step.models[0].name', 'step-3.7-flash', 'Step 文本模型', 'string', 1, 100),
('agent.ai.providers.step.models[0].type', 'TEXT', 'Step 模型类型', 'string', 1, 100),
('agent.ai.providers.step.models[0].multimodal', 'false', 'Step 模型是否多模态', 'boolean', 1, 100)
ON DUPLICATE KEY UPDATE `config_key` = VALUES(`config_key`);
