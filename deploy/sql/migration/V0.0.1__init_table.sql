create table if not exists g_user
(
    id         varchar(36) primary key comment '用户id',
    username   varchar(36) unique comment '用户名',
    password   varchar(100) comment '密码',
    email      varchar(100) comment '电子邮箱',
    birth      date comment '出生日期',
    phone      varchar(15) comment '手机号',
    `desc`     text comment '描述',
    token      varchar(64) comment 'token',
    updated_at bigint comment '最后更新时间（毫秒）',
    created_at bigint comment '创建时间（毫秒）'
) comment '用户表';

create table if not exists g_role
(
    id         varchar(36) primary key comment '战位id',
    `key`      varchar(36) unique comment '战位标识',
    `desc`     text comment '描述',
    updated_at bigint comment '最后更新时间（毫秒）',
    created_at bigint comment '创建时间（毫秒）'
) comment '战位表';

create table if not exists g_user_role
(
    id         bigint primary key auto_increment comment '用户战位id',
    user_id    varchar(36) comment '用户id',
    role_id    varchar(36) comment '战位id',
    updated_at bigint comment '最后更新时间（毫秒）',
    created_at bigint comment '创建时间（毫秒）',
    FOREIGN KEY (user_id) references g_user (id),
    FOREIGN KEY (role_id) references g_role (id)
) comment '用户战位表';

create table if not exists g_user_audit
(
    id         bigint primary key auto_increment comment '用户审计id',
    user_id    varchar(36) comment '用户id',
    role_id    varchar(36) comment '战位id',
    reason     varchar(64) comment '行为',
    ip         varchar(16) comment 'IP',
    created_at bigint comment '创建时间',
    FOREIGN KEY (user_id) references g_user (id),
    FOREIGN KEY (role_id) references g_role (id)
) comment '用户审计表';

create table if not exists g_setting
(
    `key`      varchar(128) primary key comment '配置key',
    val        text comment '配置对应的值',
    `desc`     text comment '描述',
    updated_at bigint comment '最后更新时间（毫秒）',
    created_at bigint comment '创建时间（毫秒）'
) comment '配置表';

create table if not exists g_resource
(
    id         bigint primary key auto_increment comment '用户审计id',
    name       varchar(36) comment '资源名',
    `key`      varchar(36) unique comment '资源标识',
    type       varchar(36) comment '类型',
    service_id varchar(128) unique comment '服务id',
    config     longtext comment '配置',
    updated_at bigint comment '最后更新时间（毫秒）',
    created_at bigint comment '创建时间（毫秒）'
) comment '资源表';

create table if not exists g_role_resource
(
    id          bigint primary key auto_increment comment '战位资源id',
    role_id     varchar(36) comment '战位id',
    software_id varchar(36) comment '资源id',
    updated_at  bigint comment '最后更新时间（毫秒）',
    created_at  bigint comment '创建时间（毫秒）',
    FOREIGN KEY (role_id) references g_role (id),
    FOREIGN KEY (software_id) references g_resource (service_id)
) comment '战位资源表';
