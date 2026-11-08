update g_setting
set val = '1440'
where `key` = 'system.permission.expired_time';
update g_setting
set val = '3'
where `key` = 'system.permission.password_limit';
update g_setting
set val = '60'
where `key` = 'system.permission.retry_protection';
