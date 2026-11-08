alter table g_user
    add tenx_username varchar(36) NOT NULL COMMENT '时速云关联用户名' after `avatar`;

alter table g_user
    add api_token varchar(256) NOT NULL COMMENT '时速云登录apiToken' after `avatar`;