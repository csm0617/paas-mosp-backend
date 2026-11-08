alter table g_client
    add type  varchar(20) null comment '版本类型（arm64,amd64）';

alter table g_client
    add md5  varchar(255) null comment 'md5';

alter table g_client modify column id bigint(30) COMMENT 'id' AUTO_INCREMENT;