-- AI-GENERATED AIDEV-NOTE: 迁移遗漏的全局视觉偏好，不覆盖用户选择的其他供应商。
UPDATE global_env_config
SET config_value = 'deepseek#deepseek-flash'
WHERE config_key LIKE 'agent.ai.preference%.models.%'
  AND config_value IN ('zhipu#glm-4.6v-flash', 'zhipu#GLM-4.6V-Flash', 'zhipu#glm-4.7-flash', 'zhipu#GLM-4.7-Flash');

-- 后台原来按 TEXT 类型重置多模态标记，修复已有 DeepSeek Flash 条目。
UPDATE global_env_config flags
JOIN global_env_config names
  ON flags.config_key = REPLACE(names.config_key, '.name', '.multimodal')
SET flags.config_value = 'true'
WHERE names.config_key LIKE 'agent.ai.providers.deepseek.models%.name'
  AND names.config_value = 'deepseek-flash';
