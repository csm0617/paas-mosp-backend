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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 删战位的离线端到端（真 HTTP + H2，不需要集群）。
 * <p>最有价值的一条是 {@link #batchWithProtectedRoleRollsBackEverything()}——
 * 它证明**事务边界在 Case 层**：厂商把 `@Transactional` 打在含 `forEach` 的整个方法上
 * （`RoleService.java:707`），所以"删两个、第二个是内置战位"必须整体回滚。
 * 若把事务下沉到单表 DAO，这条用例会红。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_role_delete;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K1RoleDeleteContractTest {

    private static final String ADMIN_ROLE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PROBE_ROLE_ID = "r-probe";

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        for (String table : new String[]{"g_user_role", "g_user_login", "g_user_audit", "g_role_software", "g_sub_role_role"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM g_role");

        jdbc.update("INSERT INTO g_role (id, `key`, name, `desc`, type, updated_at, created_at) VALUES (?,?,?,?,?,?,?)",
                ADMIN_ROLE_ID, "admin", "运维管理员门户", "内置", 1, 1L, 1L);
        jdbc.update("INSERT INTO g_role (id, `key`, name, `desc`, type, updated_at, created_at) VALUES (?,?,?,?,?,?,?)",
                PROBE_ROLE_ID, "probe", "探针战位", "可删", 1, 1L, 1L);

        // 五张关系表各留一行，用来验证级联真的清干净
        jdbc.update("INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (?,?,?)", "u1", PROBE_ROLE_ID, 1L);
        jdbc.update("INSERT INTO g_user_login (user_id, role_id, token, created_at) VALUES (?,?,?,?)",
                "u1", PROBE_ROLE_ID, "t1", 1L);
        jdbc.update("INSERT INTO g_user_audit (user_id, role_id, username, created_at) VALUES (?,?,?,?)",
                "u1", PROBE_ROLE_ID, "admin", 1L);
        jdbc.update("INSERT INTO g_role_software (role_id, app_id, app_name, created_at) VALUES (?,?,?,?)",
                PROBE_ROLE_ID, "app1", "应用1", 1L);
        jdbc.update("INSERT INTO g_sub_role_role (sub_role_id, role_id, created_at) VALUES (?,?,?)",
                "sr1", PROBE_ROLE_ID, 1L);
    }

    @Test
    void deleteProbeClearsAllFiveRelationTablesAndTheRoleRow() {
        ResponseEntity<Map> res = rest.exchange("/v1/role?roleId=" + PROBE_ROLE_ID, HttpMethod.DELETE, null, Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(0, body.get("code"));
        assertEquals("OK", body.get("msg"));
        assertEquals(null, body.get("data"), "厂商返回 Response.ok(null)");

        assertEquals(0, countRole(PROBE_ROLE_ID), "g_role 行应被删除");
        assertEquals(0, countRelation("g_user_role", PROBE_ROLE_ID));
        assertEquals(0, countRelation("g_user_login", PROBE_ROLE_ID));
        assertEquals(0, countRelation("g_user_audit", PROBE_ROLE_ID));
        assertEquals(0, countRelation("g_role_software", PROBE_ROLE_ID));
        assertEquals(0, countRelation("g_sub_role_role", PROBE_ROLE_ID));
        assertEquals(1, countRole(ADMIN_ROLE_ID), "内置战位不受影响");
    }

    @Test
    void deletingAdminIsRejectedWithVendorMessage() {
        ResponseEntity<Map> res = rest.exchange("/v1/role?roleId=" + ADMIN_ROLE_ID, HttpMethod.DELETE, null, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("code"));
        assertEquals("无法删除该席位", body.get("msg"), "msg 必须是消息包里的文案（不是 i18n 键）");
        assertEquals(1, countRole(ADMIN_ROLE_ID), "内置战位必须还在");
    }

    @Test
    void batchWithProtectedRoleRollsBackEverything() {
        // 先一个可删的、再一个内置的 —— 厂商会把整批回滚
        ResponseEntity<Map> res = rest.exchange("/v1/role?roleId=" + PROBE_ROLE_ID + "," + ADMIN_ROLE_ID,
                HttpMethod.DELETE, null, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("无法删除该席位", res.getBody().get("msg"));
        assertEquals(1, countRole(PROBE_ROLE_ID),
                "整批必须回滚：前一个可删战位不能留下被删的痕迹（事务边界在 Case 层）");
        assertEquals(1, countRelation("g_user_role", PROBE_ROLE_ID), "关系表也必须原样");
    }

    @Test
    void missingRoleIsRejectedWithVendorMessage() {
        ResponseEntity<Map> res = rest.exchange("/v1/role?roleId=nope", HttpMethod.DELETE, null, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("找不到该席位", res.getBody().get("msg"));
    }

    private int countRole(String id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM g_role WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private int countRelation(String table, String roleId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE role_id = ?", Integer.class, roleId);
        return n == null ? 0 : n;
    }
}
