CREATE TABLE `g_sub_role` (
  `id` varchar(36) COLLATE utf8mb4_bin NOT NULL COMMENT '席位子角色id',
  `key` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '席位子角色标识',
  `name` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '席位子角色位名',
  `desc` text COLLATE utf8mb4_bin COMMENT '描述',
  `updated_at` bigint(20) DEFAULT NULL COMMENT '最后更新时间（毫秒）',
  `created_at` bigint(20) DEFAULT NULL COMMENT '创建时间（毫秒）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='席位子角色表';

-- ----------------------------
-- Records of g_sub_role
-- ----------------------------
