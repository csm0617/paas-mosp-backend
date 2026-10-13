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

-- 审计表：删战位时要按 role_id 清（厂商级联的第 2 张表）
CREATE TABLE IF NOT EXISTS g_user_audit (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         VARCHAR(36),
    role_id         VARCHAR(36),
    rolename        VARCHAR(128),
    username        VARCHAR(36),
    reason          VARCHAR(255),
    ip              VARCHAR(64),
    created_at      BIGINT,
    request_path    VARCHAR(255),
    module_name     VARCHAR(64),
    dictionary_type VARCHAR(64)
);

-- 战位-软件关联：删战位时要按 role_id 清（厂商级联的第 5 张表）
CREATE TABLE IF NOT EXISTS g_role_software (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id    VARCHAR(36),
    app_id     VARCHAR(36),
    app_name   VARCHAR(128),
    url        VARCHAR(512),
    updated_at BIGINT,
    created_at BIGINT
);
