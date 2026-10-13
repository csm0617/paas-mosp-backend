package com.csm.paas.core;

import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 新增用户的离线端到端（真 HTTP + H2 + 模拟会话）。
 * <p>覆盖：admin 战位闸门（**非管理员是 HTTP 500，不是 403**）、重名、空口令、
 * 关系写入（战位/角色/子角色）、`uuid` 分配、口令不落明文。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_user_add;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K1UserAddContractTest {

    private static final String ADMIN_ROLE_ID = "r-admin";
    private static final String USER_ROLE_ID = "r-user";

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @MockBean
    private IUserSessionRepository userSessionRepository;

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

        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                ADMIN_ROLE_ID, "admin", "运维管理员门户", 1, 1L, 1L);
        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                USER_ROLE_ID, "user", "席位操作员", 2, 1L, 1L);
        jdbc.update("INSERT INTO g_sub_role (id, `key`, name, created_at) VALUES (?,?,?,?)", "sr-1", "srkey", "子角色A", 1L);

        // 调用者本人：admin 用户 + 它的 admin 战位关系（"是否有 admin 战位"按这张表判）
        jdbc.update("INSERT INTO g_user (id, username, password, status, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "u-caller-admin", "admin", "$2a$10$hash", 1, 1L, 1L);
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)",
                "u-caller-admin", ADMIN_ROLE_ID, 1L);

        // 调用者是 admin 用户且拥有 admin 战位
        when(userSessionRepository.find(anyString()))
                .thenReturn(Optional.of(SessionVO.builder().username("admin").roleKey("admin").build()));
    }

    @Test
    void adminCreatesUserWithRelationsAndAllocatedUuid() {
        ResponseEntity<Map> res = post("admin", """
                {"username":"bob","password":"bob123456","nickname":"鲍勃","status":1,
                 "roleListKey":["admin"],"userListKey":["user"],"subRoleListId":["sr-1"]}""");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(0, body.get("code"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals("bob", data.get("username"));
        assertEquals(0, ((Number) data.get("uuid")).intValue(), "库里没有别的带 uuid 的用户 → 分配 0");
        assertNull(data.get("password"), "重写版不下发口令哈希（刻意偏离，见 read-paths 的 L3 账）");

        String userId = (String) data.get("id");
        assertEquals(1, count("g_user", "id", userId));
        // roleListKey=["admin"]（战位 type=1）+ userListKey=["user"]（角色 type=2）→ 两条
        assertEquals(2, count("g_user_role", "user_id", userId), "战位与角色关系都已写入");
        assertEquals(1, countRoleRelation(userId, 1), "战位关系（type=1）");
        assertEquals(1, countRoleRelation(userId, 2), "角色关系（type=2）");
        assertEquals(1, count("g_user_sub_role", "user_id", userId), "子角色关系已写入");
        String hash = jdbc.queryForObject("SELECT password FROM g_user WHERE id = ?", String.class, userId);
        assertTrue(hash.startsWith("$2a$"), "库里存的必须是 BCrypt 哈希");
    }

    /**
     * `birth` 的契约：请求是 **epoch 毫秒**，响应是 **ISO-8601 UTC 串**，
     * 且"日期"语义按 JVM 默认时区的当天零点（厂商同款）——这条曾经差过 8 小时，
     * 根因是驱动版本（Connector/J 8.3 vs 厂商 5.1.48）对 DATE 列的时区语义不同，见 `DateTimes` 的文档。
     */
    @Test
    void birthRoundTripsAsCalendarDateInSystemZone() {
        ResponseEntity<Map> res = post("admin", """
                {"username":"bob","password":"bob123456","nickname":"鲍勃","status":1,
                 "birth":946771200000,"roleListKey":[]}""");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        String birth = (String) ((Map<String, Object>) res.getBody().get("data")).get("birth");
        assertNotNull(birth, "响应必须带 birth");
        assertTrue(birth.endsWith("Z"), "厂商用 UTC 的 Z 结尾：" + birth);
        assertEquals(
                java.time.LocalDate.of(2000, 1, 2).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
                java.time.Instant.parse(birth),
                "946771200000 毫秒（+08:00 的 2000-01-02 零点）应还原成同一瞬时");
    }

    @Test
    void callerWithoutAdminRoleGets500NotForbidden() {
        when(userSessionRepository.find(anyString()))
                .thenReturn(Optional.of(SessionVO.builder().username("bob").roleKey("user").build()));

        ResponseEntity<Map> res = post("any", """
                {"username":"carol","password":"carol123456","nickname":"卡罗","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode(),
                "厂商用的是 SystemException → HTTP 500（不是 403）");
        assertEquals(500, res.getBody().get("code"));
        assertEquals("席位操作员无法新增用户", res.getBody().get("msg"));
        assertEquals(0, countByUsername("carol"), "拒绝时不能留下用户");
    }

    @Test
    void duplicateUsernameIsRejected() {
        jdbc.update("INSERT INTO g_user (id, username, password, status, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "u-existing", "bob", "$2a$10$hash", 1, 1L, 1L);

        ResponseEntity<Map> res = post("admin", """
                {"username":"bob","password":"bob123456","nickname":"鲍勃","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("用户名已存在", res.getBody().get("msg"));
        assertEquals(1, countByUsername("bob"));
    }

    @Test
    void blankPasswordIsRejected() {
        ResponseEntity<Map> res = post("admin", """
                {"username":"bob","password":"   ","nickname":"鲍勃","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("密码不能为空", res.getBody().get("msg"));
        assertEquals(0, countByUsername("bob"));
    }

    @Test
    void tooShortPasswordIsRejectedByDomainRule() {
        ResponseEntity<Map> res = post("admin", """
                {"username":"bob","password":"12345","nickname":"鲍勃","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("新增的用户密码不合法(长度范围在6~36个字符以内)", res.getBody().get("msg"));
    }

    @Test
    void missingRoleListKeyDoesNotCreateHalfBakedUser() {
        // 厂商在这里会 NPE（500）**但用户行已插入**；我们按空处理且全程成功
        ResponseEntity<Map> res = post("admin", """
                {"username":"bob","password":"bob123456","nickname":"鲍勃","status":1}""");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, countByUsername("bob"));
        assertEquals(0, count("g_user_role", "user_id", (String) ((Map<?, ?>) res.getBody().get("data")).get("id")));
    }

    private ResponseEntity<Map> post(String token, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "gateway-token=" + token);
        return rest.exchange("/v1/user", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(json, headers), Map.class);
    }

    private int count(String table, String column, String value) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return n == null ? 0 : n;
    }

    private int countRoleRelation(String userId, int type) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM g_user_role ur JOIN g_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.type = ?
                """, Integer.class, userId, type);
        return n == null ? 0 : n;
    }

    private int countByUsername(String username) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM g_user WHERE username = ?", Integer.class, username);
        return n == null ? 0 : n;
    }
}
