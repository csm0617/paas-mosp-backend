package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity;
import com.csm.paas.core.domain.subrole.model.entity.SubRoleListEntity;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleRoleVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleUserVO;
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
 * 离线契约回放：厂商 `GET /v1/sub_role/switchRole` 的录制响应 → 装配器。
 * <p>样本来自**实测**：为了让列表非空，先向厂商 `POST /v1/sub_role` 建了一个探针子角色，
 * 抓完响应后 `DELETE /v1/sub_role/delete` 删除（见 docs/findings/26-e2e-user-page.md 的复现节）。
 */
class SubRoleListAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void envelopeAndItemShapeMatchRecordedVendorResponse() throws Exception {
        JsonNode vendor = load("/vendor/v1_sub_role_switchRole_nonempty.json");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(SubRoleListAssembler.toResponse(entityFromVendor(vendor))));

        assertEquals(keys(vendor), keys(mine), "信封键必须一致");
        assertEquals(keys(vendor.get("data")), keys(mine.get("data")), "data 键必须一致 {list,total}");

        Set<String> vendorItemKeys = keys(vendor.get("data").get("list").get(0));
        Set<String> mineItemKeys = keys(mine.get("data").get("list").get(0));
        assertEquals(10, mineItemKeys.size(), "厂商就是 10 个键");
        assertEquals(vendorItemKeys, mineItemKeys, "项键集合必须一个不缺一个不多");
    }

    @Test
    void emptyListSampleAlsoMatches() throws Exception {
        JsonNode vendor = load("/vendor/v1_sub_role_switchRole.json");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(SubRoleListAssembler.toResponse(entityFromVendor(vendor))));

        assertEquals(keys(vendor), keys(mine));
        assertEquals(0, mine.get("data").get("total").asInt());
        assertTrue(mine.get("data").get("list").isArray());
    }

    @Test
    void everyDeclaredFrontendReadPathHolds() throws Exception {
        JsonNode vendor = load("/vendor/v1_sub_role_switchRole_nonempty.json");
        JsonNode readPaths = load("/vendor/read-paths.json").get("GET /v1/sub_role/switchRole").get("readPaths");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(SubRoleListAssembler.toResponse(entityFromVendor(vendor))));

        List<String> checked = new ArrayList<>();
        for (JsonNode p : readPaths) {
            String path = p.get("path").asText();
            List<JsonNode> vendorValues = resolve(vendor, path);
            List<JsonNode> mineValues = resolve(mine, path);
            if (p.has("mode") && "container".equals(p.get("mode").asText())) {
                assertTrue(!mineValues.isEmpty(), "容器型路径必须存在: " + path);
            } else if (vendorValues.isEmpty()) {
                continue;
            } else {
                assertTrue(equivalentAll(vendorValues, mineValues),
                        "读取路径值必须线上等价: " + path + " vendor=" + vendorValues + " mine=" + mineValues);
            }
            checked.add(path);
        }
        assertTrue(checked.size() >= 4, "至少应校验 4 条读取路径，实际 " + checked.size());
    }

    private static SubRoleListEntity entityFromVendor(JsonNode vendor) {
        JsonNode data = vendor.get("data");
        List<SubRoleEntity> list = new ArrayList<>();
        for (JsonNode item : data.get("list")) {
            List<SubRoleUserVO> users = new ArrayList<>();
            item.path("userList").forEach(u -> users.add(SubRoleUserVO.builder()
                    .userId(u.path("userId").asText(null)).userName(u.path("userName").asText(null)).build()));
            List<SubRoleRoleVO> roles = new ArrayList<>();
            item.path("roleList").forEach(r -> roles.add(SubRoleRoleVO.builder()
                    .roleId(r.path("roleId").asText(null)).roleKey(r.path("roleKey").asText(null))
                    .roleName(r.path("roleName").asText(null)).build()));
            list.add(SubRoleEntity.builder()
                    .id(text(item, "id")).key(text(item, "key")).name(text(item, "name"))
                    .nameFr(text(item, "nameFr")).nameUs(text(item, "nameUs"))
                    .updatedAt(item.path("updatedAt").isNull() ? null : item.get("updatedAt").asLong())
                    .createdAt(item.path("createdAt").isNull() ? null : item.get("createdAt").asLong())
                    .desc(text(item, "desc"))
                    .userList(users).roleList(roles)
                    .build());
        }
        return SubRoleListEntity.builder().list(list).total(data.get("total").asLong()).build();
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
        try (InputStream in = SubRoleListAssemblerTest.class.getResourceAsStream(resource)) {
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
