CREATE TABLE `g_sub_role_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '子角色席位id',
  `sub_role_id` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '子角色id',
  `role_id` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '席位id',
  `updated_at` bigint(20) DEFAULT NULL COMMENT '最后更新时间（毫秒）',
  `created_at` bigint(20) DEFAULT NULL COMMENT '创建时间（毫秒）',
  PRIMARY KEY (`id`),
  KEY `g_sub_role_role_ibfk_1` (`sub_role_id`),
  KEY `g_sub_role_role_ibfk_2` (`role_id`),
  CONSTRAINT `g_sub_role_role_ibfk_1` FOREIGN KEY (`sub_role_id`) REFERENCES `g_sub_role` (`id`),
  CONSTRAINT `g_sub_role_role_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `g_role` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9547 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='子角色席位表';
