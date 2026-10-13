package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.UserInfoListResponseDTO;
import com.csm.paas.core.domain.user.model.entity.UserInfoEntity;
import com.csm.paas.core.domain.user.model.entity.UserInfoPageEntity;
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
 * 离线契约回放：厂商 `GET /v1/user/info` 的录制响应 → 装配器。
 * <p>断言 ① 信封/data/项键集合逐字一致（11 个键）；② `read-paths.json` 声明的每条读取路径成立。
 * <p>样本来源：运行中的厂商后端实测（`docs/findings/runtime-samples/v1_user_info.json`）。
 */
class UserInfoAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void envelopeAndItemShapeMatchRecordedVendorResponse() throws Exception {
        JsonNode vendor = load("/vendor/v1_user_info.json");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(UserInfoAssembler.toResponse(pageFromVendor(vendor))));

        assertEquals(keys(vendor), keys(mine), "信封键必须一致");
        assertEquals(keys(vendor.get("data")), keys(mine.get("data")), "data 键必须一致 {list,total}");

        Set<String> vendorItemKeys = keys(vendor.get("data").get("list").get(0));
        Set<String> mineItemKeys = keys(mine.get("data").get("list").get(0));
        assertEquals(11, mineItemKeys.size(), "厂商就是 11 个键");
        assertEquals(vendorItemKeys, mineItemKeys, "项键集合必须一个不缺一个不多");
    }

    @Test
    void nullSubRolesIsKeptAsExplicitNull() throws Exception {
        JsonNode vendor = load("/vendor/v1_user_info.json");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(UserInfoAssembler.toResponse(pageFromVendor(vendor))));

        JsonNode subRoles = mine.get("data").get("list").get(0).get("subRoles");
        assertTrue(subRoles.isNull(),
                "厂商对空子角色返回显式 null（不是 []）——前端用 `0 === subRoles?.length` 判空，两者都成立，但形状要一致");
        assertNull(vendor.get("data").get("list").get(0).get("subRoles").asText(null));
    }

    @Test
    void everyDeclaredFrontendReadPathHolds() throws Exception {
        JsonNode vendor = load("/vendor/v1_user_info.json");
        JsonNode readPaths = load("/vendor/read-paths.json").get("GET /v1/user/info").get("readPaths");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(UserInfoAssembler.toResponse(pageFromVendor(vendor))));

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
        assertTrue(checked.size() >= 11, "至少应校验 11 条读取路径，实际 " + checked.size());
    }

    /** 用厂商样本"回放"成领域读模型：等价于仓储从库里读到了同样的数据。 */
    private static UserInfoPageEntity pageFromVendor(JsonNode vendor) {
        JsonNode data = vendor.get("data");
        List<UserInfoEntity> list = new ArrayList<>();
        for (JsonNode item : data.get("list")) {
            list.add(UserInfoEntity.builder()
                    .id(text(item, "id"))
                    .nickname(text(item, "nickname"))
                    .username(text(item, "username"))
                    .status(item.get("status").asInt())
                    .updatedAt(item.get("updatedAt").asLong())
                    .createdAt(item.get("createdAt").asLong())
                    .desc(text(item, "desc"))
                    .lastLoginRole(text(item, "lastLoginRole"))
                    .roles(strings(item.get("roles")))
                    .subRoles(item.get("subRoles").isNull() ? null : strings(item.get("subRoles")))
                    .belongUsers(strings(item.get("belongUsers")))
                    .build());
        }
        return UserInfoPageEntity.builder().list(list).total(data.get("total").asLong()).build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
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
        try (InputStream in = UserInfoAssemblerTest.class.getResourceAsStream(resource)) {
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

    /** 与 tools/contract-check.py 同语义的路径解析（支持 `a.b` 与 `a.b[]`）。 */
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
