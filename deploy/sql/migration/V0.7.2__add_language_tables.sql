DROP TABLE IF EXISTS `g_language`;
CREATE TABLE `g_language`
(
    `id`           bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
    `text_key`     text NULL DEFAULT NULL COMMENT '字符标识',
    `content`      text NULL DEFAULT NULL COMMENT '文本内容',
    `lang`         text NULL DEFAULT NULL COMMENT '语言类型',
    `lang_version` text NULL DEFAULT NULL COMMENT '语言版本',
    PRIMARY KEY (`id`) USING BTREE
) COMMENT = '语言标识信息';


DROP TABLE IF EXISTS `g_language_version`;
CREATE TABLE `g_language_version`
(
    `id`           bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
    `lang`         text NULL DEFAULT NULL COMMENT '语言标识',
    `lang_version` text NULL DEFAULT NULL COMMENT '版本',
    `used`         int(10) NULL DEFAULT NULL COMMENT '是否使用 0未使用 1使用',
    `create_time`  bigint(20) NULL DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE
) COMMENT = '语言版本列表';

