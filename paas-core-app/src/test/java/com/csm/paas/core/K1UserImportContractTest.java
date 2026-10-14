package com.csm.paas.core;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户导入的离线端到端（真 HTTP multipart + H2）：厂商 `POST /v1/user/import`
 * （`UserController.java:508-518` → `UserService.handleImportExcel/importExcel`）。
 *
 * <p>覆盖验收六例里的五例（空表 / admin 行 / 新用户 / 不覆盖 / 覆盖 / 长度越界）外加 MIME 守卫。
 * 在线（真集群）另有可回滚探针，见 `docs/findings/`。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_user_import;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class K1UserImportContractTest {

    private static final String[] HEADERS = {"name", "nickname", "roleKey", "roles", "subRoles", "desc", "status", "password"};

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
        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "r-admin", "admin", "运维管理员门户", 1, 1L, 1L);
        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "r-dev", "dev", "研发", 1, 1L, 1L);
        jdbc.update("INSERT INTO g_role (id, `key`, name, type, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "r-user", "user", "席位操作员", 2, 1L, 1L);
        jdbc.update("INSERT INTO g_sub_role (id, `key`, name, created_at) VALUES (?,?,?,?)", "sr-1", "ops", "运维", 1L);
        // 实验室基线里的 admin（导入时会被跳过）
        jdbc.update("INSERT INTO g_user (id, username, password, status, updated_at, created_at) VALUES (?,?,?,?,?,?)",
                "u-admin", "admin", "$2a$10$hash", 1, 1L, 1L);
    }

    // ---------- 守卫与空表 ----------

    @Test
    void nonExcelUploadIsRejectedWithVendorMessage() {
        ResponseEntity<Map> res = upload("not an excel at all".getBytes(StandardCharsets.UTF_8), true, "a.txt");
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(400, res.getBody().get("code"));
        assertEquals("只支持xls,xlsx文件的导入", res.getBody().get("msg"));
    }

    @Test
    void headerOnlySheetIsANoOpWithOkResponse() throws IOException {
        ResponseEntity<Map> res = upload(xls(new String[0][]), true, "users.xls");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(0, res.getBody().get("code"));
        assertNull(res.getBody().get("data"), "厂商 `Response.ok(null)`");
        assertEquals(0, countUsers("bob"));
        assertEquals(0, countAll("g_user_role"), "库无任何变化");
    }

    @Test
    void adminAndRootRowsAreSkipped() throws IOException {
        ResponseEntity<Map> res = upload(xls(new String[][]{
                {"admin", "n", "admin", null, null, null, "1", "secret123"},
                {"root", "n", "admin", null, null, null, "1", "secret123"}}), true, "users.xls");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(0, countUsers("root"), "root 行被跳过");
        assertEquals(1, countAll("g_user"), "只有基线里的 admin");
    }

    // ---------- 新用户 ----------

    @Test
    void newUserIsInsertedWithRolesSubRolesAndDefaultAvatar() throws IOException {
        ResponseEntity<Map> res = upload(xls(new String[][]{
                {"bob", "鲍勃", "admin", null, "[\"ops\"]", "描述", "1", "secret123"}}), false, "users.xls");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        String userId = jdbc.queryForObject("SELECT id FROM g_user WHERE username = ?", String.class, "bob");
        assertNotNull(userId);
        assertEquals(1, count("g_user_role", "user_id", userId), "admin ⇒ 只写自身一条");
        assertEquals("r-admin", jdbc.queryForObject(
                "SELECT role_id FROM g_user_role WHERE user_id = ?", String.class, userId));
        assertEquals(1, count("g_user_sub_role", "user_id", userId));
        assertEquals("sr-1", jdbc.queryForObject(
                "SELECT sub_role_id FROM g_user_sub_role WHERE user_id = ?", String.class, userId));
        String hash = jdbc.queryForObject("SELECT password FROM g_user WHERE id = ?", String.class, userId);
        assertTrue(hash.startsWith("$2a$"), "口令必须 BCrypt");
        assertEquals(1, jdbc.queryForObject("SELECT status FROM g_user WHERE id = ?", Integer.class, userId));
        assertEquals("鲍勃", jdbc.queryForObject("SELECT nickname FROM g_user WHERE id = ?", String.class, userId));
        assertNull(jdbc.queryForObject("SELECT updated_at FROM g_user WHERE id = ?", Object.class, userId),
                "厂商 insertSelective 不写 updated_at");
        assertNotNull(jdbc.queryForObject("SELECT avatar FROM g_user WHERE id = ?", String.class, userId),
                "avatar 取厂商默认头像资源");
    }

    @Test
    void nonAdminRoleKeyAlsoWritesDefaultUserRole() throws IOException {
        upload(xls(new String[][]{{"bob", "n", "tester", "[\"dev\"]", null, null, "1", "secret123"}}), false, "u.xls");
        String userId = jdbc.queryForObject("SELECT id FROM g_user WHERE username = ?", String.class, "bob");
        assertEquals(2, count("g_user_role", "user_id", userId), "自动加 user + col3 的 dev");
    }

    // ---------- 已存在用户 ----------

    @Test
    void existingUserWithoutCoverKeepsEverything() throws IOException {
        seedExistingBob();
        upload(xls(new String[][]{{"bob", "新昵称", "dev", null, null, "新描述", "1", "secret123"}}), false, "u.xls");

        assertEquals("老昵称", jdbc.queryForObject("SELECT nickname FROM g_user WHERE id = ?", String.class, "u-bob"));
        assertEquals(0, count("g_user_role", "user_id", "u-bob"));
    }

    @Test
    void existingUserWithCoverUpdatesAndRewritesRelations() throws IOException {
        seedExistingBob();
        upload(xls(new String[][]{{"bob", "新昵称", "dev", null, "[\"ops\"]", "新描述", "1", "secret123"}}), true, "u.xls");

        assertEquals("新昵称", jdbc.queryForObject("SELECT nickname FROM g_user WHERE id = ?", String.class, "u-bob"));
        assertEquals("新描述", jdbc.queryForObject("SELECT `desc` FROM g_user WHERE id = ?", String.class, "u-bob"));
        String hash = jdbc.queryForObject("SELECT password FROM g_user WHERE id = ?", String.class, "u-bob");
        assertTrue(org.springframework.security.crypto.bcrypt.BCrypt.checkpw("secret123", hash), "口令已更新");
        assertEquals(0, jdbc.queryForObject("SELECT status FROM g_user WHERE id = ?", Integer.class, "u-bob"),
                "覆盖分支不动 status");
        assertEquals("e@x.com", jdbc.queryForObject("SELECT email FROM g_user WHERE id = ?", String.class, "u-bob"));
        assertNotNull(jdbc.queryForObject("SELECT updated_at FROM g_user WHERE id = ?", Object.class, "u-bob"));
        assertEquals(1, count("g_user_role", "user_id", "u-bob"));
        assertEquals("r-dev", jdbc.queryForObject(
                "SELECT role_id FROM g_user_role WHERE user_id = ?", String.class, "u-bob"));
        assertEquals(1, count("g_user_sub_role", "user_id", "u-bob"));
    }

    // ---------- 行内校验 ----------

    @Test
    void usernameLengthOutOfRangeIsRejected() throws IOException {
        ResponseEntity<Map> res = upload(xls(new String[][]{{"a", "n", "admin", null, null, null, "1", "secret123"}}),
                false, "u.xls");
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("导入的用户名不合法,用户名应在2~36个字符内", res.getBody().get("msg"));
        assertEquals(0, countUsers("a"));
    }

    @Test
    void missingStatusCellIsRejectedWithUsernameInMessage() throws IOException {
        ResponseEntity<Map> res = upload(xls(new String[][]{{"bob", "n", "admin", null, null, null, null, "secret123"}}),
                false, "u.xls");
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("导入的bob用户状态不能为空", res.getBody().get("msg"));
    }

    @Test
    void blankPasswordIsRejected() throws IOException {
        ResponseEntity<Map> res = upload(xls(new String[][]{{"bob", "n", "admin", null, null, null, "1", ""}}),
                false, "u.xls");
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("密码不能为空", res.getBody().get("msg"));
    }

    // ---------- 辅助 ----------

    private void seedExistingBob() {
        jdbc.update("INSERT INTO g_user (id, username, password, status, email, nickname, `desc`, avatar, "
                        + "updated_at, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                "u-bob", "bob", "$2a$10$old", 0, "e@x.com", "老昵称", "老描述", "老头像", 2L, 1L);
    }

    private static byte[] xls(String[][] dataRows) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    if (dataRows[r][c] != null) {
                        row.createCell(c).setCellValue(dataRows[r][c]);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private ResponseEntity<Map> upload(byte[] content, boolean isCover, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        body.add("isCover", String.valueOf(isCover));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, "gateway-token=admin");
        return rest.exchange("/v1/user/import", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private int count(String table, String column, String value) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return n == null ? 0 : n;
    }

    private int countAll(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private int countUsers(String username) {
        return count("g_user", "username", username);
    }
}
