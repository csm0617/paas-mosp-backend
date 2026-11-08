CREATE TABLE `g_file_project`  (
  `id` varchar(36)  NOT NULL COMMENT 'id'
  PRIMARY KEY,
  `name` varchar(128)  NULL DEFAULT NULL COMMENT '业务软件名称',
  `created_at` bigint(20) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` bigint(20) NULL DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0:否 1:是'
) comment '业务软件管理表';



CREATE TABLE `g_file`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文件id'
    PRIMARY KEY,
  `name` varchar(128)  NULL DEFAULT NULL COMMENT '文件名称',
  `file_project_id` varchar(36)  NULL DEFAULT NULL COMMENT '业务软件id',
  `path` varchar(255)  NULL DEFAULT NULL COMMENT '保存文件路径',
  `created_at` bigint(20) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` bigint(20) NULL DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0:否 1:是'
) comment '上传文件管理表';