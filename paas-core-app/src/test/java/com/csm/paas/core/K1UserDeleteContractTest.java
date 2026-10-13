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
 * 删除用户的离线端到端（真 HTTP + H2 + 模拟会话，不需要集群/Redis）。
 * <p>覆盖四条厂商语义：真删、`admin` 用户不可删、有 admin 战位者不可删另一个 admin 战位者、
 * **无 admin 战位者得到 200 假成功且目标不动**。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_user_delete;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K1UserDeleteContractTest {

    private static final String ADMIN_USER_ID = "u-admin";
    private static final String BOB_USER_ID = "u-bob";
    private static final String OTHER_ADMIN_ID = "u-otheradmin";
    private static final String ADMIN_ROLE_ID = "r-admin";
    private static final String USER_ROLE_ID = "r-user";

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    /** 会话仓储是 Redis 端口；离线测试里用假会话替代，避免依赖 Redis。 */
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

        insertUser(ADMIN_USER_ID, "admin");
        insertUser(BOB_USER_ID, "bob");
        insertUser(OTHER_ADMIN_ID, "otheradmin");

        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", ADMIN_USER_ID, ADMIN_ROLE_ID, 1L);
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", BOB_USER_ID, USER_ROLE_ID, 1L);
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", OTHER_ADMIN_ID, ADMIN_ROLE_ID, 1L);
        jdbc.update("INSERT INTO g_user_sub_role (id, user_id, sub_role_id, created_at) VALUES (?,?,?,?)",
                "usr-bob", BOB_USER_ID, "sr-x", 1L);

        // 任何 token 都解析成 admin 会话；用例里再按需改成别的调用者
        when(userSessionRepository.find(anyString()))
                .thenReturn(Optional.of(SessionVO.builder().username("admin").roleKey("admin").build()));
    }

    @Test
    void adminDeletesBobAndRelationsGoWithHim() {
        ResponseEntity<Map> res = delete("/v1/user/" + BOB_USER_ID, "admin");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("data"));
        assertEquals(0, count("g_user", "id", BOB_USER_ID));
        assertEquals(0, count("g_user_role", "user_id", BOB_USER_ID));
        assertEquals(0, count("g_user_sub_role", "user_id", BOB_USER_ID), "子角色关联也要清");
        assertEquals(1, count("g_user", "id", ADMIN_USER_ID), "别人不受影响");
    }

    @Test
    void adminCannotDeleteTheAdminUser() {
        ResponseEntity<Map> res = delete("/v1/user/" + ADMIN_USER_ID, "admin");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(400, res.getBody().get("code"));
        assertEquals("无法删除超级管理员admin用户", res.getBody().get("msg"),
                "msg 必须是消息包里的文案（role.can.not.delete.admin.key）");
        assertEquals(1, count("g_user", "id", ADMIN_USER_ID));
    }

    @Test
    void adminRoleCallerCannotDeleteAnotherAdminRoleUser() {
        when(userSessionRepository.find(anyString()))
                .thenReturn(Optional.of(SessionVO.builder().username("opadmin").roleKey("user").build()));
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", "u-opadmin", ADMIN_ROLE_ID, 1L);
        insertUser("u-opadmin", "opadmin");

        ResponseEntity<Map> res = delete("/v1/user/" + OTHER_ADMIN_ID, "any");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("普通运维管理员不能删除其他运维管理员用户，只能删除席位操作员", res.getBody().get("msg"));
        assertEquals(1, count("g_user", "id", OTHER_ADMIN_ID));
    }

    @Test
    void callerWithoutAdminRoleGets200ButNothingIsDeleted() {
        when(userSessionRepository.find(anyString()))
                .thenReturn(Optional.of(SessionVO.builder().username("bob").roleKey("user").build()));

        ResponseEntity<Map> res = delete("/v1/user/" + ADMIN_USER_ID, "any");

        assertEquals(HttpStatus.OK, res.getStatusCode(), "厂商是无权限也返回 200（不是 403）");
        assertEquals(true, res.getBody().get("data"));
        assertEquals(1, count("g_user", "id", ADMIN_USER_ID), "**目标必须纹丝不动**——这是厂商的假成功语义");
    }

    @Test
    void withoutSessionItIsUnauthorized() throws Exception {
        // 不用 TestRestTemplate：它的 SimpleClientHttpRequestFactory 走 streaming 模式时，
        // JDK 的 HttpURLConnection 会因"401 需要重试"抛 HttpRetryException，读不到响应体。
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/v1/user/" + BOB_USER_ID))
                .method("DELETE", java.net.http.HttpRequest.BodyPublishers.noBody())
                .build();
        java.net.http.HttpResponse<String> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"code\":401"), "响应体：" + response.body());
    }

    @Test
    void batchDeleteLoopsEachIdIndependently() {
        ResponseEntity<Map> res = delete("/v1/user/all_user?userId=" + BOB_USER_ID + "," + OTHER_ADMIN_ID, "admin");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(0, count("g_user", "id", BOB_USER_ID));
        // 第二个是带 admin 战位的用户：admin 用户删它不受限（分支 1 只看目标是不是 username=admin）
        assertEquals(0, count("g_user", "id", OTHER_ADMIN_ID));
        assertEquals(1, count("g_user", "id", ADMIN_USER_ID));
    }

    @Test
    void missingUserIsRejectedWithVendorMessage() {
        ResponseEntity<Map> res = delete("/v1/user/nope", "admin");

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("查无此id的用户", res.getBody().get("msg"), "user.id.not.found（实测对拉：不是 user.not.found）");
    }

    private ResponseEntity<Map> delete(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "gateway-token=" + token);
        return rest.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }

    private void insertUser(String id, String username) {
        jdbc.update("INSERT INTO g_user (id, username, password, status, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                id, username, "$2a$10$hash", 1, 1L, 1L);
    }

    private int count(String table, String column, String value) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return n == null ? 0 : n;
    }
}
