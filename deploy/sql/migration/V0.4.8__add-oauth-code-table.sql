create table g_oauth_code
(
    id             bigint primary key auto_increment,
    code           varchar(255) unique,
    authentication text,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
