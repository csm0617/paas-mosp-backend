package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.user.model.entity.UserDetailEntity;
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
 * 离线契约回放：`GET /v1/user/detailByUserId`。
 * <p>重点在**同名不同形状**：`/v1/user/info` 的 `roles` 是字符串数组，这里的 `roles` 是 **Role 对象**；
 * `belongRole` 只在这个端点出现。样本来自运行中的厂商后端实测。
 */
class UserDetailAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void detailShapeMatchesVendorSample() throws Exception {
        JsonNode vendor = load("/vendor/v1_user_detailByUserId.json");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(UserDetailAssembler.toResponse(entityFromVendor(vendor))));

        assertEquals(keys(vendor), keys(mine), "信封键一致");
        JsonNode data = vendor.get("data");
        JsonNode mineData = mine.get("data");
        assertEquals(17, keys(mineData).size(), "厂商就是 17 个键");
        assertEquals(keys(data), keys(mineData), "data 键集合必须一个不缺一个不多");
    }

    @Test
    void nestedRolesAreRoleObjectsNotStrings() throws Exception {
        JsonNode vendor = load("/vendor/v1_user_detailByUserId.json");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(UserDetailAssembler.toResponse(entityFromVendor(vendor))));

        JsonNode vendorRole = vendor.get("data").get("roles").get(0);
        JsonNode mineRole = mine.get("data").get("roles").get(0);
        assertEquals(16, keys(mineRole).size(), "内嵌的 role 是 16 键对象");
        assertEquals(keys(vendorRole), keys(mineRole));
        assertTrue(mine.get("data").get("subRoles").isArray());
        assertTrue(mine.get("data").get("belongRole").isArray());
        assertTrue(mine.get("data").get("admin").asBoolean(), "admin 有 admin 战位");
    }

    @Test
    void valuesMatchVendorForEveryScalar() throws Exception {
        JsonNode vendor = load("/vendor/v1_user_detailByUserId.json").get("data");
        JsonNode mine = ((ObjectNode) MAPPER.valueToTree(ResponseDTO.ok(
                UserDetailAssembler.toResponse(entityFromVendor(load("/vendor/v1_user_detailByUserId.json"))))))
                .get("data");

        for (String field : List.of("id", "username", "nickname", "status", "email", "birth", "phone",
                "updatedAt", "createdAt", "desc", "avatar", "lastLoginRole", "uuid", "admin")) {
            assertTrue(JsonEquivalence.equivalent(vendor.get(field), mine.get(field)),
                    field + " 必须一致：vendor=" + vendor.get(field) + " mine=" + mine.get(field));
        }
    }

    /** 用厂商样本"回放"成领域实体。 */
    private static UserDetailEntity entityFromVendor(JsonNode vendor) {
        JsonNode data = vendor.get("data");
        return UserDetailEntity.builder()
                .id(text(data, "id")).username(text(data, "username")).nickname(text(data, "nickname"))
                .status(data.get("status").asInt())
                .email(text(data, "email"))
                .birth(data.get("birth").isNull() ? null : java.time.LocalDate.parse(data.get("birth").asText().substring(0, 10)))
                .phone(text(data, "phone"))
                .updatedAt(data.get("updatedAt").asLong()).createdAt(data.get("createdAt").asLong())
                .desc(text(data, "desc")).avatar(text(data, "avatar")).lastLoginRole(text(data, "lastLoginRole"))
                .uuid(data.get("uuid").isNull() ? null : data.get("uuid").asInt())
                .roles(roles(data.get("roles")))
                .belongRole(roles(data.get("belongRole")))
                .subRoles(new ArrayList<>())
                .admin(data.get("admin").asBoolean())
                .build();
    }

    private static List<RoleEntity> roles(JsonNode array) {
        List<RoleEntity> out = new ArrayList<>();
        for (JsonNode r : array) {
            List<RoleSoftwareEntity> software = new ArrayList<>();
            for (JsonNode s : r.path("softwareList")) {
                software.add(RoleSoftwareEntity.builder()
                        .id(s.get("id").asLong()).roleId(text(s, "roleId")).appId(text(s, "appId"))
                        .appName(text(s, "appName")).url(text(s, "url"))
                        .updatedAt(s.get("updatedAt").isNull() ? null : s.get("updatedAt").asLong())
                        .createdAt(s.get("createdAt").isNull() ? null : s.get("createdAt").asLong())
                        .build());
            }
            String key = text(r, "key");
            boolean isAdmin = "admin".equals(key);
            boolean isDev = "dev".equals(key);
            out.add(RoleEntity.builder()
                    .id(text(r, "id")).key(key).name(text(r, "name"))
                    .nameFr(text(r, "nameFr")).nameUs(text(r, "nameUs"))
                    .desc(text(r, "desc")).type(r.get("type").asInt())
                    .updatedAt(r.get("updatedAt").asLong()).createdAt(r.get("createdAt").asLong())
                    .softwareList(software)
                    .admin(isAdmin).dev(isDev).adminOrDev(isAdmin || isDev)
                    .build());
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static JsonNode load(String resource) throws Exception {
        try (InputStream in = UserDetailAssemblerTest.class.getResourceAsStream(resource)) {
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
}
