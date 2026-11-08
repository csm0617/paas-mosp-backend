CREATE TABLE g_software
(
    `id`         varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '软件id',
    `key`        varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '软件标识',
    `name`       varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '软件名称',
    `app_name`   varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联应用名(在时速云上)',
    `router_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '路由标识',
    `type`       varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '软件类型',
    `desc`       text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci         NULL COMMENT '描述',
    `version`    varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '软件版本',
    `created_at` bigint(20)                                                    NULL DEFAULT NULL COMMENT '创建时间（毫秒）',
    `updated_at` bigint(20)                                                    NULL DEFAULT NULL COMMENT '最后更新时间（毫秒）',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci
  ROW_FORMAT = Dynamic;

CREATE TABLE g_software_app
(
    `id`          varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT 'id',
    `software_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT NULL COMMENT '软件id',
    `app_name`    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用名称（对应时速云应用，唯一）',
    `created_at`  bigint(20)                                                    NULL DEFAULT NULL COMMENT '创建时间（毫秒）',
    `updated_at`  bigint(20)                                                    NULL DEFAULT NULL COMMENT '最后更新时间（毫秒）',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci
  ROW_FORMAT = Dynamic;