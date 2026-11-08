alter table g_role_software
    modify id bigint auto_increment comment 'id';

alter table g_role_software
    modify app_id varchar(128) null comment '软件id';

alter table g_role_software
    modify app_name varchar(128) null comment '软件名称';

alter table g_role_software
    comment '战位软件表';

alter table g_role_software
    add `order` int default 1 not null comment '战位下的排序';
