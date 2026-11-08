-- 添加loki最大日志存储空间告警值(GB)配置
INSERT INTO `g_setting` VALUES ('system.loki.log.max.memory', '60', 'loki最大日志存储空间告警值(GB)', NULL, NULL);

-- 添加上一次loki日志存储空间删除时间配置(初始值为当前时间搓)
INSERT INTO `g_setting` VALUES ('system.loki.log.delete.last.time', UNIX_TIMESTAMP(), '上一次loki日志存储空间删除时间', NULL, NULL);

