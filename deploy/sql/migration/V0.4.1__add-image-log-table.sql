create table g_image_log
(
    id         bigint primary key auto_increment,
    file_name  varchar(256) comment '上传文件名',
    name       varchar(256) comment '镜像名',
    version    varchar(256) comment '镜像版本',
    status     int default 0 comment '状态。0：失败，1：成功，2：处理中',
    log        mediumtext comment '日志',
    created_at bigint null comment '创建时间（毫秒）',
    updated_at bigint null comment '最后更新时间（毫秒）'
) comment '镜像上传日志';
