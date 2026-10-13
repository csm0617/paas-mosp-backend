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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `GET /v1/software/{id}` 的离线端到端。
 * <p>K8s 用 mock；本环境框架不是 `BS`，所以 `serviceList` 走"厂商也置 null"的那一支。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_sw_detail;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K2SoftwareDetailContractTest {

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM g_upload_store_record");
        jdbc.update("DELETE FROM g_role_software");
        jdbc.update("DELETE FROM g_software");
        jdbc.update("DELETE FROM g_role");

        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "r-admin", "admin", "运维管理员门户", 1, 1L, 1L);
        jdbc.update("INSERT INTO g_software (id,`key`,name,router_key,type,version,created_at,framework,screenshot,icon) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                "sw-1", "myapp", "我的应用", "/myapp/", "WEB", "rec-1", 10L, "springboot", "a.png,b.png", "ICON");
        jdbc.update("INSERT INTO g_role_software (role_id, app_id, app_name, created_at) VALUES (?,?,?,?)",
                "r-admin", "sw-1", "我的应用", 1L);
        jdbc.update("INSERT INTO g_upload_store_record (id, real_store_filename, exec_file_name, updated_at, status, relation_software_id) "
                        + "VALUES (?,?,?,?,?,?)",
                "rec-1", "store.zip", "app.jar", 20L, 1, "sw-1");
    }

    @Test
    void detailReturnsVendorShapedAggregate() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/software/sw-1", Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        assertNotNull(data);
        assertEquals(23, data.size(), "厂商 SoftwareInfo 是 23 个键");
        assertEquals("myapp", data.get("key"));
        assertEquals("我的应用", data.get("name"));
        assertEquals("我的应用", data.get("nameUs"), "厂商把 nameUs 兜底成 name");
        assertEquals("我的应用", data.get("nameFr"));
        assertEquals(List.of("a.png", "b.png"), data.get("screenShotList"), "screenshot 按逗号拆");
        assertEquals(List.of("admin"), data.get("roleKeyList"));
        assertEquals(List.of("运维管理员门户"), data.get("roleNameList"));
        assertEquals("STOP", data.get("status"), "厂商字段初始化器把 status 默认成 STOP");
        assertNull(data.get("serviceList"), "非 BS 框架时厂商把 serviceList 置 null");

        Map<String, Object> record = (Map<String, Object>) data.get("uploadStoreRecord");
        assertEquals(7, record.size());
        assertEquals("rec-1", data.get("version"), "version = 当前上传记录的 id");
        assertEquals("app.jar", record.get("execFileName"));

        Map<String, Object> licence = (Map<String, Object>) data.get("licence");
        assertEquals(Set.of("end", "endIso8601", "code", "expired"), licence.keySet());
        assertEquals(-1, ((Number) licence.get("end")).intValue());
        assertNull(licence.get("endIso8601"));
        assertEquals(true, licence.get("expired"));
        assertTrue(String.valueOf(licence.get("code")).endsWith("bXlhcHA="), "软件码尾部是 Base64(\"myapp\")");
    }

    @Test
    void detailWithoutUploadRecordHasNullVersion() {
        jdbc.update("DELETE FROM g_upload_store_record");

        Map<String, Object> data = (Map<String, Object>) rest.getForEntity("/v1/software/sw-1", Map.class)
                .getBody().get("data");

        assertNull(data.get("version"));
        assertNull(data.get("uploadStoreRecord"));
    }

    @Test
    void detailRejectsUnknownIdWithItsOwnMessage() {
        ResponseEntity<Map> res = rest.getForEntity("/v1/software/nope", Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(400, res.getBody().get("code"));
    }
}
