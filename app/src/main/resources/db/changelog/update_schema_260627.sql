-- 采集任务结果可能包含多条草稿ID和错误详情，1024长度不够
ALTER TABLE gather_task MODIFY COLUMN result TEXT NULL COMMENT '任务处理结果';
