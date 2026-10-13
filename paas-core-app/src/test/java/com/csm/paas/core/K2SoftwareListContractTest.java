package com.csm.paas.core;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `GET /v1/software` 的离线端到端（真 HTTP + H2）。
 * <p>K8s 侧用 mock（`KubernetesClient` 深度桩），所以 `web` 只会走到"找不到 Deployment → 2"那条；
 * 其余分支由 `SoftwareQueryServiceImplTest` 与样本回放覆盖。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_software;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K2SoftwareListContractTest {

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM g_service_port");
        jdbc.update("DELETE FROM g_role_software");
        jdbc.update("DELETE FROM g_white_list");
        jdbc.update("DELETE FROM g_software");
        jdbc.update("DELETE FROM g_role");

        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "r-admin", "admin", "运维管理员门户", 1, 1L, 1L);
        // order_top=1 的那条必须排在前面（两级排序的第一级）
        jdbc.update("INSERT INTO g_software (id,`key`,name,router_key,type,version,created_at,updated_at,framework,order_top) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                "sw-1", "appB", "应用B", "/router/b", "WEB", "1.0", 20L, 30L, "springboot", 0);
        jdbc.update("INSERT INTO g_software (id,`key`,name,router_key,type,version,created_at,updated_at,framework,order_top) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                "sw-2", "appA", "应用A", "/router/a", "CS", "2.0", 10L, 40L, "springboot", 1);
        jdbc.update("INSERT INTO g_white_list (path, created_at) VALUES (?,?)", "/appA/**", 1L);
        jdbc.update("INSERT INTO g_role_software (role_id, app_id, app_name, created_at) VALUES (?,?,?,?)",
                "r-admin", "sw-2", "应用A", 1L);
    }

    @Test
    void listIsOrderedByOrderTopFirstAndEnriched() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/software?page=1&size=20", Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(Set.of("softwareList", "total"), keys(body.get("data")));

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("softwareList");
        assertEquals(2, items.size());
        assertEquals(2, ((Number) data.get("total")).intValue());
        assertEquals("appA", items.get(0).get("key"), "order_top=1 的必须排第一（厂商第一级排序恒为 order_top DESC）");
        assertEquals(27, items.get(0).size(), "厂商就是 27 个键");

        Map<String, Object> first = items.get(0);
        assertEquals(1, first.get("releaseStatus"), "白名单里有 /appA/** → 1");
        assertTrue(String.valueOf(first.get("url")).startsWith("gaop://cs?"), "CS 类型拼 gaop://（host 头为空时是字面量 null）");
        assertEquals(1, ((List<?>) first.get("roleList")).size(), "关联了 admin 战位");
        assertEquals(0, first.get("web"), "H2 里没有 web_entry 行 → 0");
    }

    @Test
    void nameLongerThan36IsRejected() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/software?name=" + "x".repeat(37), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(400, res.getBody().get("code"));
    }

    private static Set<String> keys(Object map) {
        return new java.util.LinkedHashSet<>(((Map<?, ?>) map).keySet().stream().map(String::valueOf).toList());
    }
}
