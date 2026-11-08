-- 添加 metric 字段
ALTER TABLE `g_alert_record`
ADD COLUMN `metric` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '指标或关键词';

-- 添加 operator 字段
ALTER TABLE `g_alert_record`
ADD COLUMN `operator` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '比较关系';

-- 添加 value 字段
ALTER TABLE `g_alert_record`
ADD COLUMN `value` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '告警阈值';