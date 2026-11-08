create table g_service_port
(
    id           bigint primary key auto_increment,
    software_id  varchar(36) comment '软件id',
    service_name varchar(128) comment '服务名（唯一标识）',
    port         int comment '映射端口，同一个服务下唯一',
    path_name    varchar(256) comment '端口映射的路径名',
    web_entry    boolean comment '是否为web入口'
) comment '服务端口映射表';
