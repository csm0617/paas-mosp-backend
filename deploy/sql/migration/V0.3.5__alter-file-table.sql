alter table g_file
    add current_size bigint(30) null comment '当前文件实际大小（字节）' after deleted;
alter table g_file
    add md5 varchar(36) null comment '文件md5值';