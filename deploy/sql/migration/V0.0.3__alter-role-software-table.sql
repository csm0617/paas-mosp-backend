alter table g_role_resource
    drop foreign key g_role_resource_ibfk_2;

alter table g_role_resource
    change software_id app_id varchar(128) null comment '应用id（对应时速云应用名称）';

rename table g_role_resource to g_role_software;

alter table g_role_software
    add app_name varchar(128) null comment '应用名称（自定义字段）' after app_id;

alter table g_role_software
    add url varchar(4096) null comment '访问地址' after app_name;

drop table if exists g_resource;

