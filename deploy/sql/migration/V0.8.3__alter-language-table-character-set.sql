ALTER TABLE `g_language`
    MODIFY COLUMN `text_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字符标识' AFTER `id`;
DELETE
from `g_mode`
where mode_name = 'MAINTAIN';
