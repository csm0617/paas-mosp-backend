ALTER TABLE `gaop`.`g_software`
    ADD COLUMN `location` varchar(30) NULL COMMENT 'cs软件连接方式 本地 local 远程 remote' AFTER `icon`,
ADD COLUMN `remote_ip` varchar(50) NULL COMMENT '远程连接ip' AFTER `location`,
ADD COLUMN `remote_name` varchar(50) NULL COMMENT '远程连接用户名' AFTER `remote_ip`,
ADD COLUMN `remote_password` varchar(50) NULL COMMENT '远程连接密码' AFTER `remote_name`;





update g_software s set location='local'
WHERE s.framework='CS';