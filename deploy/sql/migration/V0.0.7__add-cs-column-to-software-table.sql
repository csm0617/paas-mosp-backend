alter table g_software
    add framework varchar(100) default 'BS' not null comment '软件框架，BS 或 CS';
alter table g_software
    add exec_name varchar(200) null comment 'framework 为 CS 才有效，用于启动和监控CS主程序';
