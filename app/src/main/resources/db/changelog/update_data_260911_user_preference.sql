-- 智谱下线：用户级模型偏好中的智谱模型统一改写为 DeepSeek V4.1 Flash
-- 注意：ai_user_preference 由 Hibernate 维护，MySQL 环境可能不存在，changeSet 侧已加 tableExists 前置条件

UPDATE `ai_user_preference` SET `preference` = REPLACE(`preference`, 'zhipu#glm-4.6v-flash', 'deepseek#deepseek-flash') WHERE `preference` LIKE '%zhipu#glm-4.6v-flash%';
UPDATE `ai_user_preference` SET `preference` = REPLACE(`preference`, 'zhipu#GLM-4.6V-Flash', 'deepseek#deepseek-flash') WHERE `preference` LIKE '%zhipu#GLM-4.6V-Flash%';
UPDATE `ai_user_preference` SET `preference` = REPLACE(`preference`, 'zhipu#glm-4.7-flash', 'deepseek#deepseek-flash') WHERE `preference` LIKE '%zhipu#glm-4.7-flash%';
UPDATE `ai_user_preference` SET `preference` = REPLACE(`preference`, 'zhipu#GLM-4.7-Flash', 'deepseek#deepseek-flash') WHERE `preference` LIKE '%zhipu#GLM-4.7-Flash%';
