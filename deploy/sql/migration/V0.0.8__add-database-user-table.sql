create table g_database_user
(
    id         bigint       not null comment '用户id'
        primary key auto_increment,
    username   varchar(36)  null comment '数据库管理用户名',
    password   varchar(100) null comment '密码',
    `desc`     text         null comment '描述',
    updated_at bigint       null comment '最后更新时间（毫秒）',
    created_at bigint       null comment '创建时间（毫秒）'
) comment '数据库用户表';
