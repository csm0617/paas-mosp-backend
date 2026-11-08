INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (100, 0, 'BEHAVIOR_TYPE', '行为类型');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (101, 100, 'USER', '用户');
INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (102, 100, 'ROLE', '战位');
INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (103, 100, 'APPLICATION', '应用');
INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (104, 100, 'SERVICE', '服务');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (105, 100, 'BACKUP', '备份还原');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (106, 100, 'CLIENT', '框架客户端');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (107, 100, 'CONFIG_MAP', '服务配置组');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (108, 100, 'CS_SOFTWARE', '应用客户端');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (109, 100, 'DATABASE', '数据库');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (110, 100, 'DATABASE_USER', '数据库用户');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (111, 100, 'GLOBALSETTING', '全局参数');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (112, 100, 'IMAGE', '镜像');


INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (113, 100, 'PLATFORM_AUTH', '平台授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (114, 100, 'AUTH', '服务授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (200, 0, 'OPERATION_TYPE', '操作类型');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (201, 200, 'CREATE', '创建');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (202, 200, 'UPDATE', '更新');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (203, 200, 'DELETE', '删除');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (204, 200, 'EXPORT', '导出');
INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (205, 200, 'IMPORT', '导入');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (206, 200, 'INSTALL', '安装');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (207, 200, 'STOP', '停止');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (208, 200, 'START', '启动');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (209, 200, 'RESTART', '重启');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (210, 200, 'BACKUP', '备份');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (211, 200, 'DOWNLOAD', '下载');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (212, 200, 'RESET', '重置');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (213, 200, 'UPLOAD', '上传');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (214, 200, 'PLATFORM_ACTIVATE', '平台授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (215, 200, 'PLATFORM_DEACTIVATE', '平台取消授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (216, 200, 'SERVICE_AUTH', '服务授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (217, 200, 'CANCEL_SERVICE_AUTH', '取消服务授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (218, 200, 'SOFTWARE_AUTH', '软件授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (219, 200, 'SOFTWARE_CANCEL_AUTH', '软件取消授权');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (220, 200, 'LOGIN', '登录');

INSERT INTO `gaop`.`g_dictionary`(`id`, `parent_id`, `dictionary_key`, `dictionary_name`)
VALUES (221, 200, 'LOGOUT', '注销');

create index audit_mod on g_user_audit (module_name);