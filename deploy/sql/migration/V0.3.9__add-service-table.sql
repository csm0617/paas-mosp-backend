CREATE TABLE `g_service`
(
    `id`           bigint(30)  NOT NULL AUTO_INCREMENT COMMENT '主键id'
        PRIMARY KEY,
    `service_name` varchar(50) NULL DEFAULT NULL COMMENT '服务名称',
    `min_memory`   varchar(50) NULL DEFAULT NULL COMMENT '最小内存 单位:MB',
    `max_memory`   varchar(50) NULL DEFAULT NULL COMMENT '最大内存 单位:MB',
    `min_cpu`      varchar(30) NULL DEFAULT NULL COMMENT '最小cpu大小 单位:MB',
    `max_cpu`      varchar(30) NULL DEFAULT NULL COMMENT '最大cpu大小 单位:MB',
    `create_by`    varchar(50)  NULL DEFAULT NULL COMMENT '创建人主键',
    `create_name`  varchar(50) NULL DEFAULT NULL COMMENT '创建人用户名',
    `create_date`  bigint(40)  NULL DEFAULT NULL COMMENT '创建时间 时间戳',
    `update_date`  bigint(40)  NULL DEFAULT NULL COMMENT '更新时间 时间戳',
    `deleted`       int(20)     NULL DEFAULT NULL COMMENT '是否删除 0已删除 1未删除'

) COMMENT = '服务列表';