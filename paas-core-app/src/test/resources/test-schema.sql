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
    `order`    INT,
    updated_at BIGINT,
    created_at BIGINT
);

-- K2（软件目录）契约测试用
CREATE TABLE IF NOT EXISTS g_software (
    id              VARCHAR(36) PRIMARY KEY,
    `key`           VARCHAR(36) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    app_name        VARCHAR(128),
    router_key      VARCHAR(128),
    type            VARCHAR(128),
    `desc`          CLOB,
    version         VARCHAR(255),
    created_at      BIGINT,
    updated_at      BIGINT,
    framework       VARCHAR(100) NOT NULL DEFAULT '',
    exec_name       VARCHAR(200),
    active_code     CLOB,
    screenshot      CLOB,
    icon            CLOB,
    location        VARCHAR(30),
    remote_ip       VARCHAR(50),
    remote_name     VARCHAR(50),
    remote_password VARCHAR(50),
    active_create   BIGINT,
    active_expire   BIGINT,
    order_top       INT DEFAULT 0,
    order_time      BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS g_white_list (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    path       VARCHAR(255),
    created_at BIGINT
);

CREATE TABLE IF NOT EXISTS g_service_port (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    software_id  VARCHAR(36),
    service_name VARCHAR(128),
    port         INT,
    path_name    VARCHAR(256),
    web_entry    TINYINT,
    allow        TINYINT NOT NULL DEFAULT 1,
    updated_at   BIGINT
);

CREATE TABLE IF NOT EXISTS g_software_app (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    software_id VARCHAR(36),
    app_name    VARCHAR(128)
);

-- K2：软件当前版本的上传记录（详情端点的 version/uploadStoreRecord 来源）
CREATE TABLE IF NOT EXISTS g_upload_store_record (
    id                  VARCHAR(36) PRIMARY KEY,
    real_store_filename VARCHAR(512),
    exec_file_name      VARCHAR(255),
    updated_at          BIGINT,
    exec_fault_message  VARCHAR(512),
    status              INT,
    relation_software_id VARCHAR(36)
);

-- 厂商 gaop-9.sql:167-182 原样（CicdController 的 g_ci_cd）
CREATE TABLE IF NOT EXISTS g_ci_cd (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    user_id            VARCHAR(36)  DEFAULT NULL,
    image_name         VARCHAR(50)  DEFAULT NULL,
    image_tag          VARCHAR(50)  DEFAULT NULL,
    dockerfile_id      VARCHAR(50)  DEFAULT NULL,
    dockerfile_content VARCHAR(2000) DEFAULT NULL,
    buildfile_id       VARCHAR(50)  DEFAULT NULL,
    job_name           VARCHAR(50)  DEFAULT NULL,
    job_status         INT          DEFAULT NULL,
    description        VARCHAR(255) DEFAULT NULL,
    create_at          BIGINT       DEFAULT NULL,
    update_at          BIGINT       DEFAULT NULL,
    image_storage      VARCHAR(100) DEFAULT NULL
);

-- g_service_auth 的 upsert 会写 auth_code/created_at/updated_at（authing 端点）
ALTER TABLE g_service_auth ADD COLUMN IF NOT EXISTS auth_code  CLOB;
ALTER TABLE g_service_auth ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE g_service_auth ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
