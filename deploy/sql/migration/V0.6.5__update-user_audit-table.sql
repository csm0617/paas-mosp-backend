alter table g_user_audit
    add request_path varchar(100) null comment '访问路径';
alter table g_user_audit
    add module_name varchar(36) null comment '模块名字典key';

ALTER TABLE `g_user_audit`
    ADD COLUMN `dictionary_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '操作类型' AFTER `module_name`;

CREATE TABLE `g_dictionary`
(
    `id`              int(30) NOT NULL AUTO_INCREMENT COMMENT '模块名字典主键',
    `parent_id`       int(11) null comment '父级id',
    `dictionary_key`  varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '对应的表g_user-audit中的key',
    `dictionary_name` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'key模块对应的中文value',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

