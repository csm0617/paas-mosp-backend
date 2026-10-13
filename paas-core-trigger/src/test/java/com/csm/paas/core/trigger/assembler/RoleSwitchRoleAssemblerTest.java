package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;
import com.csm.paas.core.trigger.JsonEquivalence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线契约回放：`/v1/role/switchRole`（两种 type）与 `/v1/role/{id}`。
 * <p>样本来自运行中的厂商后端实测（`docs/findings/runtime-samples/`）。
 * <p>本测试的重点是**四个集合的 null/数组区分**——这是这四个端点唯一真正的差别，
 * 也是最容易被"顺手统一"破坏的地方。
 */
class RoleSwitchRoleAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void switchRoleType1KeepsAllFourCollections() throws Exception {
        JsonNode vendor = load("/vendor/v1_role_switchRole_type1.json");
        ObjectNode mine = renderList(vendor);

        JsonNode vendorItem = vendor.get("data").get("list").get(0);
        JsonNode mineItem = mine.get("data").get("list").get(0);
        assertEquals(16, keys(mineItem).size(), "厂商就是 16 个键");
        assertEquals(keys(vendorItem), keys(mineItem), "项键集合必须一个不缺一个不多");

        assertTrue(mineItem.get("onLineUserList").isArray(), "switchRole 的 onLineUserList 必须是数组（不是 null）");
        assertEquals(keys(vendorItem.get("softwareList").get(0)), keys(mineItem.get("softwareList").get(0)),
                "软件项键集合（12 个）必须一致");
        assertEquals(keys(vendorItem.get("userList").get(0)), keys(mineItem.get("userList").get(0)));
        assertTrue(mineItem.get("subRoleList").isArray());
    }

    @Test
    void switchRoleType2KeepsEmptyArraysNotNull() throws Exception {
        JsonNode vendor = load("/vendor/v1_role_switchRole_type2.json");
        ObjectNode mine = renderList(vendor);

        JsonNode mineItem = mine.get("data").get("list").get(0);
        assertEquals(2, mineItem.get("type").asInt(), "type=2 是角色（不是战位）");
        for (String field : List.of("onLineUserList", "softwareList", "userList", "subRoleList")) {
            assertTrue(mineItem.get(field).isArray(), field + " 必须是空数组而不是 null");
        }
    }

    @Test
    void roleDetailHasAllFourCollectionsNullButFillsNameUsAndNameFr() throws Exception {
        JsonNode vendor = load("/vendor/v1_role_detail.json");
        JsonNode data = vendor.get("data");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(RoleListAssembler.toItemDTO(detailEntity(data))));
        JsonNode mineData = mine.get("data");

        assertEquals(keys(data), keys(mineData), "详情也是 16 键");
        for (String field : List.of("onLineUserList", "softwareList", "userList", "subRoleList")) {
            assertTrue(mineData.get(field).isNull(),
                    "/v1/role/{id} 的 " + field + " 必须是 null（与 switchRole 相反）");
        }
        assertEquals(mineData.get("name").asText(), mineData.get("nameUs").asText(), "厂商把 nameUs 兜底成 name");
        assertEquals(mineData.get("name").asText(), mineData.get("nameFr").asText(), "厂商把 nameFr 兜底成 name");
    }

    @Test
    void everyDeclaredFrontendReadPathHoldsForAllThreeEndpoints() throws Exception {
        assertReadPaths("GET /v1/role/switchRole", load("/vendor/v1_role_switchRole_type1.json"),
                renderList(load("/vendor/v1_role_switchRole_type1.json")), 13);
        assertReadPaths("GET /v1/role/{id}", load("/vendor/v1_role_detail.json"),
                (ObjectNode) MAPPER.valueToTree(ResponseDTO.ok(
                        RoleListAssembler.toItemDTO(detailEntity(load("/vendor/v1_role_detail.json").get("data"))))),
                6);
    }

    private static void assertReadPaths(String endpoint, JsonNode vendor, ObjectNode mine, int minChecked) throws Exception {
        JsonNode readPaths = load("/vendor/read-paths.json").get(endpoint).get("readPaths");
        int checked = 0;
        for (JsonNode p : readPaths) {
            String path = p.get("path").asText();
            List<JsonNode> vv = resolve(vendor, path);
            List<JsonNode> mv = resolve(mine, path);
            if (p.has("mode") && "container".equals(p.get("mode").asText())) {
                assertTrue(!mv.isEmpty(), "容器型路径必须存在: " + path);
            } else if (vv.isEmpty()) {
                continue;
            } else {
                assertTrue(equivalentAll(vv, mv), endpoint + " 读取路径必须线上等价: " + path
                        + " vendor=" + vv + " mine=" + mv);
            }
            checked++;
        }
        assertTrue(checked >= minChecked, endpoint + " 至少应校验 " + minChecked + " 条读取路径，实际 " + checked);
    }

    // ---------- helpers ----------

    private static ObjectNode renderList(JsonNode vendor) {
        JsonNode data = vendor.get("data");
        List<RoleEntity> roles = new ArrayList<>();
        for (JsonNode item : data.get("list")) {
            roles.add(entity(item, true));
        }
        return (ObjectNode) MAPPER.valueToTree(ResponseDTO.ok(
                RoleListAssembler.toResponse(RoleListEntity.builder().list(roles).total(data.get("total").asLong()).build())));
    }

    private static RoleEntity detailEntity(JsonNode data) {
        return entity(data, false);
    }

    private static RoleEntity entity(JsonNode item, boolean withCollections) {
        String key = item.get("key").asText();
        boolean isAdmin = "admin".equals(key);
        boolean isDev = "dev".equals(key);
        return RoleEntity.builder()
                .id(item.get("id").asText())
                .key(key)
                .name(item.get("name").asText())
                .nameFr(item.get("nameFr").isNull() ? null : item.get("nameFr").asText())
                .nameUs(item.get("nameUs").isNull() ? null : item.get("nameUs").asText())
                .desc(item.get("desc").isNull() ? null : item.get("desc").asText())
                .type(item.get("type").asInt())
                .updatedAt(item.get("updatedAt").asLong())
                .createdAt(item.get("createdAt").asLong())
                .softwareList(software(item.get("softwareList")))
                .userList(users(item.get("userList")))
                .onLineUserList(withCollections && item.get("onLineUserList").isArray()
                        ? toStrings(item.get("onLineUserList")) : null)
                .subRoleList(withCollections && item.get("subRoleList").isArray()
                        ? List.of() : null)
                .admin(isAdmin).dev(isDev).adminOrDev(isAdmin || isDev)
                .build();
    }

    private static List<RoleSoftwareEntity> software(JsonNode array) {
        if (array == null || array.isNull()) {
            return null;
        }
        List<RoleSoftwareEntity> out = new ArrayList<>();
        for (JsonNode s : array) {
            out.add(RoleSoftwareEntity.builder()
                    .id(s.get("id").asLong())
                    .roleId(text(s, "roleId"))
                    .appId(text(s, "appId"))
                    .appName(text(s, "appName"))
                    .url(text(s, "url"))
                    .updatedAt(s.get("updatedAt").isNull() ? null : s.get("updatedAt").asLong())
                    .createdAt(s.get("createdAt").isNull() ? null : s.get("createdAt").asLong())
                    .build());
        }
        return out;
    }

    private static List<RoleUserVO> users(JsonNode array) {
        if (array == null || array.isNull()) {
            return null;
        }
        List<RoleUserVO> out = new ArrayList<>();
        for (JsonNode u : array) {
            out.add(RoleUserVO.builder().userId(text(u, "userId")).userName(text(u, "userName")).build());
        }
        return out;
    }

    private static List<String> toStrings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static boolean equivalentAll(List<JsonNode> a, List<JsonNode> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!JsonEquivalence.equivalent(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static JsonNode load(String resource) throws Exception {
        try (InputStream in = RoleSwitchRoleAssemblerTest.class.getResourceAsStream(resource)) {
            return MAPPER.readTree(in);
        }
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> out = new LinkedHashSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    private static List<JsonNode> resolve(JsonNode root, String path) {
        List<JsonNode> current = new ArrayList<>();
        current.add(root);
        for (String segment : path.split("\\.")) {
            List<JsonNode> next = new ArrayList<>();
            boolean wildcard = segment.endsWith("[]");
            String field = wildcard ? segment.substring(0, segment.length() - 2) : segment;
            for (JsonNode node : current) {
                JsonNode value = node.path(field);
                if (value.isMissingNode()) {
                    return List.of();
                }
                if (wildcard) {
                    if (value.isArray()) {
                        value.forEach(next::add);
                    } else {
                        return List.of();
                    }
                } else {
                    next.add(value);
                }
            }
            current = next;
        }
        return current;
    }
}
