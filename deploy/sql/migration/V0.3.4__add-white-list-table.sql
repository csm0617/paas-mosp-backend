CREATE TABLE `g_white_list`
(
    `id`         bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id'
        primary key,
    `path`       varchar(36)  NULL DEFAULT NULL COMMENT '放行的访问路径',
    `created_at` bigint(20)   NULL DEFAULT NULL COMMENT '创建时间',
    `updated_at` bigint(20)   NULL DEFAULT NULL COMMENT '更新时间'
)comment '白名单';