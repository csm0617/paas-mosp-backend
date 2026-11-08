ALTER TABLE `g_service`
    MODIFY COLUMN `service_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '服务名称' AFTER `id`;
ALTER TABLE `g_user`
    MODIFY COLUMN `tenx_username` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '时速云关联用户名' AFTER `api_token`,
    MODIFY COLUMN `last_login_role` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '最后登录的战位的key' AFTER `tenx_username`,
    MODIFY COLUMN `nickname` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '昵称' AFTER `last_login_role`;
ALTER TABLE `g_user_audit`
    MODIFY COLUMN `rolename` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '战位名称' AFTER `role_id`,
    MODIFY COLUMN `username` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '登录用户名' AFTER `rolename`,
    MODIFY COLUMN `request_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '访问路径' AFTER `created_at`,
    MODIFY COLUMN `module_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '模块名字典key' AFTER `request_path`;
