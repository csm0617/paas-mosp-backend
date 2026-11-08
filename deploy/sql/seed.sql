-- ============================================================================
-- paas-core 最小初始化数据（空库时可登录）
--
-- 为什么需要它：厂商的初始化 SQL 把 g_user / g_user_role 的 INSERT 注释掉了，
-- 用户与门户由厂商安装器在平台侧写入。自研后端直接灌 87 个迁移只得到 39 张空表，
-- 登录页的「门户/席位」下拉会为空（GET /v1/role?username=admin 无关联行）。
--
-- ★ 口令是测试值：admin / admin123。投产前必须替换 password 列的 BCrypt 串。
--   生成方式（任选其一，输出 $2a$10$... 形式）：
--     htpasswd -bnBC 10 "" '新口令' | tr -d ':\n'
--     python3 -c "import bcrypt;print(bcrypt.hashpw(b'新口令',bcrypt.gensalt(10)).decode())"
--   替换后执行：UPDATE g_user SET password='<新串>' WHERE username='admin';
--
-- 角色 key 来自代码常量 RoleConstant.ADMIN_KEY = 'admin'，
-- 名称取自 ADMIN_PERMISSION = '运维管理员门户'；type=1 表示「战位」。
-- ============================================================================
SET NAMES utf8mb4;
SET @now = UNIX_TIMESTAMP() * 1000;

INSERT INTO g_role (id, `key`, name, `desc`, updated_at, created_at, type)
VALUES ('11111111-1111-1111-1111-111111111111', 'admin', '运维管理员门户',
        '平台运维管理员门户（部署包初始化）', @now, @now, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type), updated_at = @now;

INSERT INTO g_user (id, username, password, status, nickname, `desc`, updated_at, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'admin',
        '$2a$10$l1gMJx2Qyp4CnZQO5yFJ9.Lur16Bk.BIAtCL78RLh5uukrgN.UUCC',
        1, '管理员', '平台管理员（部署包初始化）', @now, @now)
ON DUPLICATE KEY UPDATE password = VALUES(password), status = 1, updated_at = @now;

INSERT IGNORE INTO g_user_role (user_id, role_id, updated_at, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', @now, @now);

-- 自查：三行都应存在
SELECT 'role' AS t, `key`, name, CAST(type AS CHAR) AS v FROM g_role WHERE `key` = 'admin'
UNION ALL
SELECT 'user', username, nickname, CAST(status AS CHAR) FROM g_user WHERE username = 'admin'
UNION ALL
SELECT 'mapping', CAST(user_id AS CHAR), role_id, '1' FROM g_user_role
WHERE user_id = '22222222-2222-2222-2222-222222222222';
