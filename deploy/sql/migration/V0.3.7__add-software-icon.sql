alter table g_software
    add icon longtext null comment '软件图标';

alter table g_user
    add last_login_role varchar(36) null comment '最后登录的战位的key';