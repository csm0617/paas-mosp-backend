CREATE TABLE `g_service_auth`
(
    `service_id`    varchar(255) NOT NULL COMMENT '服务id',
    `auth`          tinyint(2)            DEFAULT NULL COMMENT '是否授权',
    `auth_type`     int(11)               DEFAULT '1' COMMENT '授权方式：一次性认证(1)、定时认证(2)',
    `auth_time`     bigint(20)            DEFAULT NULL COMMENT '授权到期时间',
    `auth_interval` bigint(20)            DEFAULT NULL COMMENT '授权间隔',
    `updated_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`service_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
