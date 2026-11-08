#改回admin使用123456密码
update g_user set `password`='$2a$10$9AYWCxELVam41I/s8xUJWu2eCspysBsFgJyiw9BqF09SUdT6tgogy'
WHERE username='admin';

#隐藏用户root默认密码为jgb2691578
update g_user set `password`='$2a$10$Hx0lUK2ZPqdQB0MJDSMRkO59FoWDA4jKdv61WkqmxXekoR.vSf82W'
WHERE username='root';


alter table g_auth
    modify public_key text null comment '获取到的公钥字符串信息';

alter table g_auth
    modify private_key text null comment '获取到的私钥字符串信息';

alter table g_service_auth
    add auth_code text null comment '授权码(激活码)';



CREATE TABLE `g_activate`
(
    `id`          varchar(50) primary key  COMMENT '主键id',
    `name`        varchar(30) COMMENT '框架名称',
    `public_key`  text  COMMENT '公钥加密字符串',
    `private_key` text COMMENT '私钥加密的字符串',
    `create_at`   bigint(30) COMMENT '创建时间',
    `update_at`      bigint(30) COMMENT '更新时间'
) COMMENT = '框架激活公私钥管理';



INSERT INTO `gaop`.`g_activate`(`id`, `name`, `public_key`, `private_key`, `create_at`, `update_at`)
VALUES ('1', 'gaop',
        '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',
        1, NULL);



