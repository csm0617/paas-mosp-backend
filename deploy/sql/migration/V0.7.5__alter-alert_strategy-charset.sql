# 修改 g_alert_strategy 表的字符集为utf8mb4
alter table g_alert_strategy default character set utf8mb4;
alter table g_alert_strategy convert to character set utf8mb4;
alter table g_alert_strategy MODIFY strategy_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;