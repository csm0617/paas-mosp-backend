package com.csm.paas.core;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切片 3a 的离线端到端（真 HTTP + H2 + 厂商消息包，**不需要集群**）：
 * <ol>
 *   <li>{@code GET /v1/user/info} 的形状与领域规则（跳过 root、subRoles 为显式 null）；</li>
 *   <li>400 错误响应的厂商语义（code 400 + **i18n 文案** + data.{trace,message}）；</li>
 *   <li>404 **不套信封**（还原 Spring 默认错误体，见 docs/findings/24-error-contract.md）。</li>
 * </ol>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_k1;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K1UserInfoContractTest {

    private static final String ADMIN_ROLE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ROLE_ID_USER = "3";
    private static final String ADMIN_USER_ID = "22222222-2222-2222-2222-222222222222";

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM g_user_role");
        jdbc.update("DELETE FROM g_user_sub_role");
        jdbc.update("DELETE FROM g_sub_role");
        jdbc.update("DELETE FROM g_user");
        jdbc.update("DELETE FROM g_role");

        jdbc.update("INSERT INTO g_role (id, `key`, name, `desc`, type, updated_at, created_at) VALUES (?,?,?,?,?,?,?)",
                ADMIN_ROLE_ID, "admin", "运维管理员门户", "平台运维管理员门户", 1, 1791908324000L, 1791908324000L);
        jdbc.update("INSERT INTO g_role (id, `key`, name, `desc`, type, updated_at, created_at) VALUES (?,?,?,?,?,?,?)",
                ROLE_ID_USER, "user", "席位操作员", "席位操作员", 2, 1662372001168L, 1594799004879L);

        jdbc.update("INSERT INTO g_user (id, username, password, status, nickname, `desc`, last_login_role, updated_at, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                ADMIN_USER_ID, "admin", "$2a$10$hash", 1, "管理员", "平台管理员", "admin",
                1791908324000L, 1791908324000L);
        // 第二个用户：**没有**子角色，用来验证 subRoles 是显式 null
        jdbc.update("INSERT INTO g_user (id, username, password, status, nickname, updated_at, created_at) "
                        + "VALUES (?,?,?,?,?,?,?)",
                "u-operator", "operator", "$2a$10$hash", 1, "操作员", 100L, 100L);
        // 厂商硬编码跳过 root（但 total 仍会算上它）
        jdbc.update("INSERT INTO g_user (id, username, password, status, nickname, updated_at, created_at) "
                        + "VALUES (?,?,?,?,?,?,?)",
                "u-root", "root", "$2a$10$hash", 1, "root", 1L, 1L);

        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", ADMIN_USER_ID, ADMIN_ROLE_ID, 1L);
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", ADMIN_USER_ID, ROLE_ID_USER, 1L);
        jdbc.update("INSERT INTO g_user_sub_role (id, user_id, sub_role_id, created_at) VALUES (?,?,?,?)",
                "usr-1", ADMIN_USER_ID, "sr-1", 1L);
        jdbc.update("INSERT INTO g_sub_role (id, `key`, name, created_at) VALUES (?,?,?,?)", "sr-1", "srkey", "子角色A", 1L);
    }

    @Test
    void userInfoReturnsVendorShapedPageAndSkipsRoot() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/user/info?page=1&size=20", Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals("OK", body.get("msg"));
        assertEquals(0, body.get("code"));
        assertEquals(Set.of("list", "total"), keys(body.get("data")));

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) data.get("list");
        assertEquals(2, list.size(), "root 必须被跳过");
        assertEquals(3, ((Number) data.get("total")).intValue(), "total 来自库计数，含 root（厂商行为）");

        Map<String, Object> item = list.stream()
                .filter(i -> "admin".equals(i.get("username"))).findFirst().orElseThrow();
        Map<String, Object> withoutSubRole = list.stream()
                .filter(i -> "operator".equals(i.get("username"))).findFirst().orElseThrow();

        assertEquals(Set.of("id", "nickname", "username", "status", "updatedAt", "createdAt",
                        "desc", "lastLoginRole", "roles", "subRoles", "belongUsers"),
                keys(item), "项键集合必须与厂商一致（11 个）");
        assertEquals("管理员", item.get("nickname"));
        assertEquals(java.util.List.of("运维管理员门户"), item.get("roles"), "type=1 的名字");
        assertEquals(java.util.List.of("席位操作员"), item.get("belongUsers"),
                "厂商字段名错位：belongUsers 装的是 type=2 的名字");
        assertEquals(java.util.List.of("子角色A"), item.get("subRoles"));
        assertTrue(withoutSubRole.containsKey("subRoles"), "subRoles 必须是显式键");
        assertNull(withoutSubRole.get("subRoles"), "无子角色 → 显式 null（厂商行为）");
        // 无 cookie 也 200：鉴权在网关侧（docs/findings/14-vendor-auth-is-gateway-side.md）
    }

    @Test
    void keywordTooLongYieldsVendor400WithResolvedI18nMessage() {
        String tooLong = "a".repeat(37);
        ResponseEntity<Map> res = rest.getForEntity("/v1/user/info?keyword=" + tooLong, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("code"));
        assertEquals("搜索长度不合法，搜索关键字(用户名)的长度应在0~36个字符之内", body.get("msg"),
                "msg 必须是**消息包里的文案**，不是 i18n 键");
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals(Set.of("trace", "message"), keys(data), "厂商 data 是 {trace,message}");
        assertEquals(body.get("msg"), data.get("message"));
        assertTrue(String.valueOf(data.get("trace")).contains("WrongArgumentException"),
                "trace 应是异常堆栈");
    }

    @Test
    void subRoleSwitchRoleReturnsVendorShapedListWithRelations() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/sub_role/switchRole?page=1&size=9999", Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(0, body.get("code"));
        assertEquals(Set.of("list", "total"), keys(body.get("data")));

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) data.get("list");
        assertEquals(1, list.size());
        assertEquals(1, ((Number) data.get("total")).intValue(), "空关键字分支：total 来自计数");

        Map<String, Object> item = list.get(0);
        assertEquals(Set.of("id", "key", "name", "nameFr", "nameUs", "updatedAt", "createdAt",
                "desc", "userList", "roleList"), keys(item), "项键集合必须与厂商一致（10 个）");
        assertEquals("子角色A", item.get("name"));
        // 关系集合在列表接口里恒为数组（实测样本），不是 null
        assertEquals(java.util.List.of(Map.of("userId", ADMIN_USER_ID, "userName", "admin")), item.get("userList"));
        assertEquals(java.util.List.of(), item.get("roleList"));
    }

    @Test
    void unmappedPathStaysSpring404NotEnvelope() {        ResponseEntity<Map> res = rest.getForEntity("/v1/definitely_not_mapped_xyz", Map.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertNull(body.get("msg"), "404 不得套厂商信封");
        assertEquals(Set.of("timestamp", "status", "error", "message", "path"), keys(body),
                "必须是 Spring 默认错误体（实测厂商行为）");
        assertEquals(404, body.get("status"));
    }

    /**
     * 405（路径匹配、方法不对）**必须走 -1 信封**：厂商跑在 Spring 5.3 上，这类异常从 `handleNoMatch`
     * 抛出后被它的 `Throwable` 兜底吃掉 → HTTP 500 + `code:-1` + `msg:"系统错误"`（2026-10-14 实测三条）。
     * <p>与上面 404 的对比很关键：**不是所有框架级错误都还原 Spring 默认体**。
     */
    @Test
    void methodNotAllowedBecomesVendorMinusOneEnvelope() {
        ResponseEntity<Map> res = rest.exchange("/v1/user", HttpMethod.DELETE, null, Map.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(-1, body.get("code"), "厂商对 405 返回 code -1（不是 405）");
        assertEquals("系统错误", body.get("msg"));
        assertEquals(Set.of("trace", "message"), keys(body.get("data")));
    }

    private static Set<String> keys(Object map) {
        Set<String> out = new LinkedHashSet<>();
        for (Iterator<?> it = ((Map<?, ?>) map).keySet().iterator(); it.hasNext(); ) {
            out.add(String.valueOf(it.next()));
        }
        return out;
    }
}
