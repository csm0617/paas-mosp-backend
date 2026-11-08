alter table g_upload_store_record
    change status status bigint(30) NULL DEFAULT NULL COMMENT '操作状态';

alter table g_database_record
    change status status bigint(30) NULL DEFAULT NULL COMMENT '操作状态';