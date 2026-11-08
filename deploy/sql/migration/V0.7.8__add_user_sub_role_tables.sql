CREATE TABLE `g_user_sub_role` (
                                   `id` varchar(36) COLLATE utf8mb4_bin NOT NULL COMMENT '用户席位子角色id',
                                   `user_id` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户id',
                                   `sub_role_id` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '席位子角色id',
                                   `updated_at` bigint(20) DEFAULT NULL COMMENT '最后更新时间（毫秒）',
                                   `created_at` bigint(20) DEFAULT NULL COMMENT '创建时间（毫秒）',
                                   PRIMARY KEY (`id`)

) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户席位子角色表';
