#增加快速开始界面关闭设置
INSERT INTO `gaop`.`g_setting`(`key`, `val`, `desc`, `updated_at`, `created_at`) VALUES ('system.init.quick_start', 'false', '是否关闭快速开始界面', NULL, NULL);



#修改部分战位为角色数据
UPDATE g_role set name='运维管理员',`desc`='运维管理员' WHERE `key`='admin';

UPDATE g_role set name='开发管理员',`desc`='开发管理员' WHERE `key`='dev';