alter table g_user_audit
    add username varchar(36) NULL COMMENT '登录用户名' after `role_id`;

alter table g_user_audit
    add rolename varchar(36) NULL COMMENT '战位名称' after `role_id`;

update g_user_audit ua
    left join g_role r on r.id = ua.role_id
    left join g_user u on u.id = ua.user_id
set ua.username = u.username,
    ua.rolename = r.name
where ua.username is null
   or ua.rolename is null;
