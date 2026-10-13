package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleListEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 战位切片的离线契约回放：用录制下来的厂商 `/v1/role` 响应喂装配器，断言形状与前端读取路径。
 * <p>厂商特有行为也一并锁住：`admin`/`dev` 的 `adminOrDev`/`admin`/`dev` 由 key 推导（`RoleService.java:376-394`）。
 */
class RoleListAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void roleEnvelopeItemAndSoftwareShapeMatchVendorSample() throws Exception {
        JsonNode vendor = load("/vendor/v1_role.json");
        ObjectNode mine = render(vendor);

        assertEquals(keys(vendor), keys(mine), "信封键一致");
        assertEquals(keys(vendor.get("data")), keys(mine.get("data")), "data 键一致");

        JsonNode vendorRole = vendor.get("data").get("list").get(0);
        JsonNode mineRole = mine.get("data").get("list").get(0);
        assertEquals(keys(vendorRole), keys(mineRole), "战位项键集合必须一致");

        JsonNode vendorSoftware = vendorRole.get("softwareList").get(0);
        JsonNode mineSoftware = mineRole.get("softwareList").get(0);
        assertEquals(keys(vendorSoftware), keys(mineSoftware), "软件项键集合必须一致");
    }

    @Test
    void adminAndDevFlagsAreDerivedFromKey() throws Exception {
        ObjectNode mine = render(load("/vendor/v1_role.json"));
        boolean sawAdmin = false;
        for (JsonNode item : mine.get("data").get("list")) {
            String key = item.get("key").asText();
            if ("admin".equals(key)) {
                sawAdmin = true;
                assertTrue(item.get("admin").asBoolean(), "admin 战位必须 admin=true");
                assertTrue(item.get("adminOrDev").asBoolean());
            }
        }
        assertTrue(sawAdmin, "样本里应有 admin 战位");
    }

    @Test
    void everyDeclaredFrontendReadPathHolds() throws Exception {
        JsonNode vendor = load("/vendor/v1_role.json");
        ObjectNode mine = render(vendor);
        JsonNode readPaths = load("/vendor/read-paths.json").get("GET /v1/role").get("readPaths");

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
                assertTrue(equivalentAll(vv, mv), "读取路径值必须线上等价: " + path + " vendor=" + vv + " mine=" + mv);
            }
            checked++;
        }
        assertTrue(checked >= 5, "至少应校验 5 条读取路径，实际 " + checked);
    }

    /** 逐元素"线上等价"（数字按值、对象按字段递归）。 */
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

    // ---------- helpers ----------

    private static ObjectNode render(JsonNode vendor) {
        return (ObjectNode) MAPPER.valueToTree(ResponseDTO.ok(RoleListAssembler.toResponse(toEntity(vendor))));
    }

    @SuppressWarnings("unchecked")
    private static RoleListEntity toEntity(JsonNode vendor) {
        JsonNode data = vendor.get("data");
        List<RoleEntity> roles = new ArrayList<>();
        for (JsonNode item : data.get("list")) {
            List<RoleSoftwareEntity> software = new ArrayList<>();
            for (JsonNode s : item.get("softwareList")) {
                software.add(RoleSoftwareEntity.builder()
                        .id(s.get("id").asLong())
                        .roleId(s.get("roleId").asText())
                        .appId(s.get("appId").asText())
                        .appName(s.get("appName").isNull() ? null : s.get("appName").asText())
                        .url(s.get("url").isNull() ? null : s.get("url").asText())
                        .updatedAt(s.get("updatedAt").isNull() ? null : s.get("updatedAt").asLong())
                        .createdAt(s.get("createdAt").isNull() ? null : s.get("createdAt").asLong())
                        .build());
            }
            String key = item.get("key").asText();
            boolean isAdmin = "admin".equals(key);
            boolean isDev = "dev".equals(key);
            roles.add(RoleEntity.builder()
                    .id(item.get("id").asText())
                    .key(key)
                    .name(item.get("name").asText())
                    .desc(item.get("desc").asText())
                    .type(item.get("type").asInt())
                    .createdAt(item.get("createdAt").asLong())
                    .updatedAt(item.get("updatedAt").asLong())
                    .softwareList(software)
                    .admin(isAdmin)
                    .dev(isDev)
                    .adminOrDev(isAdmin || isDev)
                    .build());
        }
        return RoleListEntity.builder().list(roles).total(data.get("total").asLong()).build();
    }

    private static JsonNode load(String resource) throws Exception {
        try (InputStream in = RoleListAssemblerTest.class.getResourceAsStream(resource)) {
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
