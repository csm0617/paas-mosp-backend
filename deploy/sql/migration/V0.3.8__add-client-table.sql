CREATE TABLE `g_client`
(
    `id`                    varchar(255)  NOT NULL COMMENT 'id'
        primary key,
    `client_filename`       varchar(255)  NULL DEFAULT NULL COMMENT '客户端文件名',
    `client_store_filename` varchar(255)  NULL DEFAULT NULL COMMENT '客户端存储路径文件名',
    `version`               varchar(30)   NULL DEFAULT NULL COMMENT '版本',
    `updated_at`            bigint(20)    NULL DEFAULT NULL COMMENT '上传时间'
)  COMMENT = 'gaop客户端版本信息表';