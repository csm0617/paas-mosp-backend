CREATE TABLE `g_database`
(
    id              bigint      not null comment '数据库id'
        primary key auto_increment,
    `database_name` varchar(36) NOT NULL COMMENT '数据库名',
    `desc`          text        NULL COMMENT '描述'
) comment '数据库列表';