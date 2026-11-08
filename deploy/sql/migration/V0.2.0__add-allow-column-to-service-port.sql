alter table g_service_port
    add allow boolean default true not null;

alter table g_service_port
    add updated_at bigint null;
