alter table g_software
    add active_create bigint(30) null comment '授权时创建时间(时间戳)';

alter table g_software
    add active_expire bigint(30) null comment '授权时过期时间(时间戳)';


alter table g_role
    add type int(10) null comment '类型 1战位 2角色';

update g_role set type=2
WHERE `key` in ('admin','dev');

update g_role set type=1
WHERE `key`  not in ('admin','dev');