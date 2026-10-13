-- 契约测试用的最小 schema：只建切片用到的表。
-- 列定义以**实库**为准（docs/findings/23-k1-live-schema-and-unique-constraints.md），不是从迁移文件推的。

CREATE TABLE IF NOT EXISTS g_service_auth (
    service_id    VARCHAR(255) NOT NULL,
    auth          TINYINT DEFAULT NULL,
    auth_type     INT DEFAULT 1,
    auth_time     BIGINT DEFAULT NULL,
    auth_interval BIGINT DEFAULT NULL,
    PRIMARY KEY (service_id)
);

-- K1（切片 3）：用户 / 战位 / 子角色
CREATE TABLE IF NOT EXISTS g_user (
    id              VARCHAR(36) PRIMARY KEY,
    username        VARCHAR(36) UNIQUE,
    password        VARCHAR(100),
    status          TINYINT NOT NULL,
    email           VARCHAR(100),
    birth           DATE,
    phone           VARCHAR(15),
    `desc`          CLOB,
    updated_at      BIGINT,
    created_at      BIGINT,
    avatar          CLOB,
    api_token       VARCHAR(256),
    tenx_username   VARCHAR(128),
    last_login_role VARCHAR(128),
    nickname        VARCHAR(128),
    uuid            INT
);

CREATE TABLE IF NOT EXISTS g_role (
    id         VARCHAR(36) PRIMARY KEY,
    `key`      VARCHAR(36) UNIQUE,
    name       VARCHAR(128) NOT NULL,
    `desc`     CLOB,
    updated_at BIGINT,
    created_at BIGINT,
    type       INT
);

CREATE TABLE IF NOT EXISTS g_user_role (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    VARCHAR(36),
    role_id    VARCHAR(36),
    updated_at BIGINT,
    created_at BIGINT
);

CREATE TABLE IF NOT EXISTS g_user_login (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    VARCHAR(36),
    role_id    VARCHAR(36),
    token      VARCHAR(36),
    logout_at  BIGINT,
    created_at BIGINT
);

CREATE TABLE IF NOT EXISTS g_sub_role (
    id         VARCHAR(36) PRIMARY KEY,
    `key`      VARCHAR(36) UNIQUE,
    name       VARCHAR(128) NOT NULL,
    `desc`     CLOB,
    updated_at BIGINT,
    created_at BIGINT
);

CREATE TABLE IF NOT EXISTS g_user_sub_role (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36),
    sub_role_id VARCHAR(36),
    updated_at  BIGINT,
    created_at  BIGINT
);

CREATE TABLE IF NOT EXISTS g_sub_role_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    sub_role_id VARCHAR(36),
    role_id     VARCHAR(36),
    updated_at  BIGINT,
    created_at  BIGINT
);
