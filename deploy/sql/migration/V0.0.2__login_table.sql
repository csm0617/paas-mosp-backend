create table g_user_login
(
    id         bigint auto_increment primary key,
    user_id    varchar(36),
    role_id    varchar(36),
    token      varchar(36),
    logout_at bigint comment '注销时间（毫秒）',
    created_at bigint comment '创建时间（毫秒）'
) comment '用户登录表';
