alter table g_user
    add status tinyint(4) NOT NULL COMMENT '状态：0-禁用，1-启用' after `password`;

alter table g_user
    drop column `token`;