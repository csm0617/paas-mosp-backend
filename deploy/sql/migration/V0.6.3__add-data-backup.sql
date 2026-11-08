CREATE TABLE `g_backup`
(
    `id`                bigint(30) primary key AUTO_INCREMENT COMMENT '主键id',
    `database_key`      varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备份数据库字段标识',
    `database_name`     varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备份数据库名',
    `database_filename` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备份后文件名称',
    `size`              bigint(40) NULL DEFAULT NULL COMMENT '文件大小',
    `created_at`        bigint(20) NULL DEFAULT NULL COMMENT '创建时间',
    `updated_at`        bigint(20) NULL DEFAULT NULL COMMENT '更新时间',
    `msg`               varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '异常信息'
) COMMENT = '框架备份列表管理';

INSERT INTO `g_backup`
VALUES (1, 'user', '用户数据', 'user.xls', NULL, 1675051200, NULL, NULL);
INSERT INTO `g_backup`
VALUES (2, 'role', '席位数据', 'role.xls', NULL, 1675051200, NULL, NULL);
INSERT INTO g_backup (id, database_key, database_name, database_filename, size, created_at, updated_at, msg)
VALUES (3, 'sub_role', '子角色数据', 'sub_role.xls', null, 1675051200, null, null);
INSERT INTO `g_backup`
VALUES (4, 'software', '软件数据', 'software.json', NULL, 1675051200, NULL, NULL);
INSERT INTO `g_backup`
VALUES (5, 'ingress', '负载均衡', 'ingress.json', NULL, 1675051200, NULL, NULL);
INSERT INTO `g_backup`
VALUES (6, 'configmap', '服务配置', 'configmap.json', NULL, 1675051200, NULL, NULL);
INSERT INTO `g_backup`
VALUES (7, 'image', '服务镜像', '*.tar.gz', NULL, 1675051200, NULL, NULL);
INSERT INTO `g_backup`
VALUES (8, 'database', '业务数据', 'dataBase_backup.zip', NULL, 1675051200, NULL, NULL);

CREATE TABLE `g_backup_setting`
(
    `id`            bigint(30) primary key AUTO_INCREMENT COMMENT '主键id',
    `database_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据库名称',
    `type`          int(10) NULL DEFAULT NULL COMMENT '备份类型 0仅数据结构 1数据结构与数据'
) COMMENT = '业务数据库备份设置表';