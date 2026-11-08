ALTER TABLE `g_language` 
MODIFY COLUMN `text_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字符标识' AFTER `id`,
ADD COLUMN `description` varchar(1000) NULL COMMENT '描述' AFTER `text_key`,
MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '文本内容-全称' AFTER `text_key`,
ADD COLUMN `abbr` varchar(255) NULL COMMENT '文本内容-缩写' AFTER `content`,
MODIFY COLUMN `lang` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '语言类型' AFTER `content`,
MODIFY COLUMN `lang_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '语言版本' AFTER `lang`,
ADD COLUMN `module` varchar(255) NOT NULL COMMENT '模块标识' AFTER `lang_version`,
ADD UNIQUE INDEX `uni_lang_version_module_key`(`lang`, `lang_version`, `module`(20), `text_key`(500)) USING BTREE COMMENT '同一语言版本的模块下key唯一';


ALTER TABLE `g_language_version` 
MODIFY COLUMN `lang` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '语言标识' AFTER `id`,
MODIFY COLUMN `lang_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '版本' AFTER `lang`,
MODIFY COLUMN `used` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否使用 0未使用 1使用' AFTER `lang_version`,
MODIFY COLUMN `create_time` bigint(20) NOT NULL DEFAULT 0 COMMENT '创建时间' AFTER `used`,
ADD UNIQUE INDEX `uni_lan_version`(`lang`, `lang_version`) USING BTREE COMMENT '语言和版本唯一',
ADD INDEX `idx_used`(`used`) USING BTREE COMMENT '使用中版本查询';



update g_language_version set used=0 where used=1;
# 默认语言
INSERT into g_language_version(lang, lang_version, used) values
("zh_CN", "default", 1),
("en_US", "default", 0),
("fr_FR", "default", 0);