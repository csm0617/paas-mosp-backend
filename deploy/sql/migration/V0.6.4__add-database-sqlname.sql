alter table g_database
    add sql_name varchar(60) null comment '存储在服务器上的sql文件名称';

alter table g_database
    add username varchar(40) null comment '最后操作的用户名';
    
INSERT INTO `gaop`.`g_setting`(`key`, `val`, `desc`, `updated_at`, `created_at`)
VALUES ('alarm.cpu.lower_limit', '10', 'CPU空闲告警阈值(百分比)', NULL, NULL);
INSERT INTO `gaop`.`g_setting`(`key`, `val`, `desc`, `updated_at`, `created_at`)
VALUES ('alarm.cpu.up_limit', '80', 'CPU高占用告警阈值(百分比)', NULL, NULL);
INSERT INTO `gaop`.`g_setting`(`key`, `val`, `desc`, `updated_at`, `created_at`)
VALUES ('alarm.memory.lower_limit', '10', '内存空闲告警阈值下限(百分比)', NULL, NULL);
INSERT INTO `gaop`.`g_setting`(`key`, `val`, `desc`, `updated_at`, `created_at`)
VALUES ('alarm.memory.up_limit', '80', '内存高占用告警阈值上限(百分比)', NULL, NULL);
