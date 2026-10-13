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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 修改用户的离线端到端（真 HTTP + H2 + 模拟会话）。
 * <p>三条权限规则里最反直觉的是第 3 条：普通用户改别人 → **HTTP 500**（厂商用 SystemException），不是 403。
 * <p>另一组重点是"哪些字段能改"：厂商把 **email/birth/phone 强制写回原值**，且 `desc` 空白时置**空串**。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_user_update;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K1UserUpdateContractTest {

    private static final String ADMIN_ROLE_ID = "r-admin";
    private static final String USER_ROLE_ID = "r-user";
    private static final String ADMIN_USER_ID = "u-admin";
    private static final String BOB_ID = "u-bob";

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @MockBean
    private IUserSessionRepository userSessionRepository;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM g_user_role");
        jdbc.update("DELETE FROM g_user_sub_role");
        jdbc.update("DELETE FROM g_user");
        jdbc.update("DELETE FROM g_role");

        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                ADMIN_ROLE_ID, "admin", "运维管理员门户", 1, 1L, 1L);
        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                USER_ROLE_ID, "user", "席位操作员", 2, 1L, 1L);

        insertUser(ADMIN_USER_ID, "admin", "old@x.com", "13900000000");
        insertUser(BOB_ID, "bob", null, null);
        insertUser("u-opadmin", "opadmin", null, null);

        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", ADMIN_USER_ID, ADMIN_ROLE_ID, 1L);
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", BOB_ID, USER_ROLE_ID, 1L);
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", "u-opadmin", ADMIN_ROLE_ID, 1L);

        sessionAs("admin");
    }

    @Test
    void adminUpdatesBobAndProtectedFieldsStayUntouched() {
        ResponseEntity<Map> res = put(BOB_ID, "any", """
                {"username":"bob","nickname":"新昵称","status":0,"desc":"",
                 "email":"hacked@x.com","phone":"13800000000","birth":946771200000,
                 "roleListKey":["admin"],"userListKey":[]}""");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        assertNotNull(data);
        assertEquals("新昵称", data.get("nickname"));
        assertEquals(0, data.get("status"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM g_user WHERE id = ?", BOB_ID);
        assertEquals("", row.get("desc"), "修改时空白 desc 置空串（新增时是 null）");
        assertEquals(0, ((Number) row.get("status")).intValue());
        // 关系被替换：bob 现在有 admin 战位（type=1）且没有角色（type=2）
        assertEquals(1, countRoleRelation(BOB_ID, 1));
        assertEquals(0, countRoleRelation(BOB_ID, 2));
    }

    @Test
    void ordinaryUserCanUpdateSelf() {
        sessionAs("bob");
        ResponseEntity<Map> res = put(BOB_ID, "any", """
                {"username":"bob","nickname":"我自己改的","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("我自己改的", jdbc.queryForObject("SELECT nickname FROM g_user WHERE id = ?", String.class, BOB_ID));
    }

    @Test
    void ordinaryUserUpdatingSomeoneElseGets500() {
        sessionAs("bob");
        ResponseEntity<Map> res = put(ADMIN_USER_ID, "any", """
                {"username":"admin","nickname":"我不该能改","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode(),
                "厂商用 SystemException → HTTP 500（不是 403）");
        assertEquals(500, res.getBody().get("code"));
        assertEquals("席位操作员不能修改其他用户信息", res.getBody().get("msg"));
        assertEquals("管理员", jdbc.queryForObject("SELECT nickname FROM g_user WHERE id = ?", String.class, ADMIN_USER_ID));
    }

    @Test
    void adminRoleCallerCannotModifyTheAdminUser() {
        sessionAs("opadmin");
        ResponseEntity<Map> res = put(ADMIN_USER_ID, "any", """
                {"username":"admin","nickname":"改不动","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("普通运维管理员席位无法修改admin超级运维管理员席位!", res.getBody().get("msg"));
        assertEquals("管理员", jdbc.queryForObject("SELECT nickname FROM g_user WHERE id = ?", String.class, ADMIN_USER_ID));
    }

    @Test
    void missingTargetIsRejected() {
        ResponseEntity<Map> res = put("nope", "any", """
                {"username":"x","nickname":"x","status":1,"roleListKey":[]}""");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("用户不存在", res.getBody().get("msg"), "user.not.found");
    }

    @Test
    void withoutSessionItIsUnauthorized() throws Exception {
        // 用 JDK HttpClient 而不是 TestRestTemplate：后者的 SimpleClientHttpRequestFactory 处于 streaming
        // 模式时，HttpURLConnection 会因"401 需要重试"抛 HttpRetryException，读不到响应体
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/v1/user/" + BOB_ID))
                .header("Content-Type", "application/json")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofString("{\"nickname\":\"x\"}"))
                .build();
        java.net.http.HttpResponse<String> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"code\":401"), "响应体：" + response.body());
    }

    private void sessionAs(String username) {
        when(userSessionRepository.find(anyString()))
                .thenReturn(Optional.of(SessionVO.builder().username(username).roleKey("user").build()));
    }

    private ResponseEntity<Map> put(String userId, String token, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "gateway-token=" + token);
        return rest.exchange("/v1/user/" + userId, HttpMethod.PUT, new HttpEntity<>(json, headers), Map.class);
    }

    private void insertUser(String id, String username, String email, String phone) {
        jdbc.update("INSERT INTO g_user (id, username, password, status, nickname, email, phone, updated_at, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                id, username, "$2a$10$hash", 1, "管理员", email, phone, 1L, 1L);
    }

    private int countRoleRelation(String userId, int type) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM g_user_role ur JOIN g_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.type = ?
                """, Integer.class, userId, type);
        return n == null ? 0 : n;
    }

    // ---------- GET /v1/user/{userId} / userToRole / {userId}/roles ----------

    @Test
    void userByIdReturnsVendorShapedUserWithNullPassword() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/user/" + BOB_ID, Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        assertEquals(14, data.size(), "厂商就是 14 个键");
        assertEquals("bob", data.get("username"));
        org.junit.jupiter.api.Assertions.assertNull(data.get("password"), "厂商这个端点把口令置 null");
    }

    @Test
    void userByIdRejectsUnknownIdWithItsOwnMessage() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/user/nope", Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("查询无此用户ID", res.getBody().get("msg"),
                "user.id.query.not.found（与删用户的 user.id.not.found 不是同一个键）");
    }

    @Test
    void userToRoleReplacesAllRelations() {
        ResponseEntity<Map> res = rest.exchange("/v1/user/userToRole", HttpMethod.POST,
                jsonEntity("{\"userId\":\"" + BOB_ID + "\",\"roleIdList\":[\"" + ADMIN_ROLE_ID + "\",\"" + USER_ROLE_ID + "\"]}"),
                Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("data"));
        assertEquals(2, countAllRoleRelations(BOB_ID), "全部关联被替换成两条");
    }

    @Test
    void userToRoleRejectsUnknownUser() {
        ResponseEntity<Map> res = rest.exchange("/v1/user/userToRole", HttpMethod.POST,
                jsonEntity("{\"userId\":\"nope\",\"roleIdList\":[\"r\"]}"), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("找不到改用户信息，无法进行关联操作", res.getBody().get("msg"));
    }

    @Test
    void relatePositionsOnlyReplacesType1() {
        ResponseEntity<Map> res = rest.exchange("/v1/user/" + BOB_ID + "/roles", HttpMethod.POST,
                jsonEntity("[\"" + ADMIN_ROLE_ID + "\"]"), Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNull(res.getBody().get("data"), "厂商返回 Response.ok(null)");
        assertEquals(1, countRoleRelation(BOB_ID, 1), "战位被替换成 admin");
        assertEquals(1, countRoleRelation(BOB_ID, 2),
                "type=2 的角色关联**不受影响**——厂商的 deleteRelateUserToRole 只删指定类型");
    }

    @Test
    void relatePositionsRejectsUnknownUser() {
        ResponseEntity<Map> res = rest.exchange("/v1/user/nope/roles", HttpMethod.POST,
                jsonEntity("[\"" + ADMIN_ROLE_ID + "\"]"), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("用户不存在", res.getBody().get("msg"), "user.not.found");
    }

    private HttpEntity<String> jsonEntity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "gateway-token=any");
        return new HttpEntity<>(json, headers);
    }

    private int countAllRoleRelations(String userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM g_user_role WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }
}
