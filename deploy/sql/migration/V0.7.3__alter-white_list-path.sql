ALTER TABLE `g_white_list` 
MODIFY COLUMN `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '放行的访问路径' AFTER `id`;