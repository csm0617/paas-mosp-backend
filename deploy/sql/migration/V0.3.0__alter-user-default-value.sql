alter table g_user
    modify tenx_username varchar(36)  NULL COMMENT '时速云关联用户名';

alter table g_user
    modify api_token varchar(256)  NULL COMMENT '时速云登录apiToken';