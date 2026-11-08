CREATE TABLE `g_upload_store_record`
(
    `id`                  varchar(255) NOT NULL COMMENT 'id'
        primary key,
    `real_store_filename` varchar(255) NULL DEFAULT NULL COMMENT '真实路径文件',
    `exec_file_name`      varchar(255) NULL DEFAULT NULL COMMENT '显示文件',
    `updated_at`          bigint(20)   NULL DEFAULT NULL COMMENT '操作时间',
    `exec_fault_message`  varchar(255) NULL DEFAULT NULL COMMENT '报错信息',
    `status`              varchar(255)  NULL DEFAULT NULL COMMENT '操作状态'
) comment '仓库管理-文件上传记录';