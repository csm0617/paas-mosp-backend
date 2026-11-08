DROP TABLE IF EXISTS `g_group`;
CREATE TABLE `g_group`
(
    `id`          bigint(30) NOT NULL AUTO_INCREMENT COMMENT '主键id',
    `name`        text NULL DEFAULT NULL COMMENT '仓库组名',
    `create_date` bigint(40) NULL DEFAULT NULL COMMENT '创建时间 时间戳',
    PRIMARY KEY (`id`) USING BTREE
) COMMENT = '镜像仓库组';