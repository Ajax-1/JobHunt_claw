-- 智谱下线（DeepSeek V4.1 Flash 全面替代）：清理采集模型字典项并迁移存量采集配置
DELETE FROM `common_dict` WHERE `app` = 'gather' AND `dict_key` = 'GatherModelEnum' AND `dict_value` = 'ZhiPu';

UPDATE `gather_task` SET `model` = 'DeepSeek' WHERE `model` = 'ZhiPu';
UPDATE `gather_task` SET `model` = 'deepseek#deepseek-flash' WHERE `model` LIKE 'zhipu#%';

UPDATE `gather_source` SET `model` = 'DeepSeek' WHERE `model` = 'ZhiPu';
UPDATE `gather_source` SET `model` = 'deepseek#deepseek-flash' WHERE `model` LIKE 'zhipu#%';
