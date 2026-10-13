-- 契约测试用的最小 schema：只建切片用到的表
CREATE TABLE IF NOT EXISTS g_service_auth (
    service_id    VARCHAR(255) NOT NULL,
    auth          TINYINT DEFAULT NULL,
    auth_type     INT DEFAULT 1,
    auth_time     BIGINT DEFAULT NULL,
    auth_interval BIGINT DEFAULT NULL,
    PRIMARY KEY (service_id)
);
