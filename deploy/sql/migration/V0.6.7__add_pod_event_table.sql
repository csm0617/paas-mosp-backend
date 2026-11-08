CREATE TABLE `g_pod_event`
(
    `id`            int(36) NOT NULL AUTO_INCREMENT COMMENT '主键id',
    `service_name`  varchar(50) NULL DEFAULT NULL COMMENT '服务名称',
    `pod_name`      varchar(60) NULL DEFAULT NULL COMMENT '容器名称',
    `create_at`     bigint(30) NULL DEFAULT NULL COMMENT '重启时间',
    `restart_count` int(30) NULL DEFAULT NULL COMMENT '重启次数',
    PRIMARY KEY (`id`) USING BTREE
)COMMENT = '容器重启记录表';

