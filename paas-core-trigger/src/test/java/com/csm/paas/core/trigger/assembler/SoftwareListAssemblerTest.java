package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareListEntity;
import com.csm.paas.core.domain.software.model.entity.SoftwareMessageEntity;
import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;
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
 * 离线契约回放：`GET /v1/software`。
 * <p>样本是**实测录制**的：为了拿到非空列表，往厂商库里临时插了 3 行 `g_software`
 * （覆盖 `CS`/`WEB` 两种 `url` 分支、`releaseStatus` 0/1、`web` 0/2、`roleList` 非空与 null），
 * 抓完即删。录制时与厂商**整体 JSON 相等**，见 `docs/findings/37`。
 */
class SoftwareListAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void itemShapeMatchesRecordedVendorResponse() throws Exception {
        JsonNode vendor = load("/vendor/v1_software_probe.json");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(SoftwareListAssembler.toResponse(entityFromVendor(vendor))));

        assertEquals(keys(vendor), keys(mine));
        JsonNode vendorItem = vendor.get("data").get("softwareList").get(0);
        JsonNode mineItem = mine.get("data").get("softwareList").get(0);
        assertEquals(27, keys(mineItem).size(), "厂商就是 27 个键（Software 23 + 4）");
        assertEquals(keys(vendorItem), keys(mineItem), "项键集合必须一个不缺一个不多");
    }

    @Test
    void everyValueIsReproducedFromTheRecording() throws Exception {
        JsonNode vendor = load("/vendor/v1_software_probe.json");
        JsonNode data = vendor.get("data");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(
                ResponseDTO.ok(SoftwareListAssembler.toResponse(entityFromVendor(vendor))));

        assertEquals(data.get("total").asLong(), mine.get("data").get("total").asLong());
        JsonNode vs = data.get("softwareList");
        JsonNode ms = mine.get("data").get("softwareList");
        assertEquals(vs.size(), ms.size());
        for (int i = 0; i < vs.size(); i++) {
            JsonNode v = vs.get(i);
            JsonNode m = ms.get(i);
            // 不能用 assertEquals(JsonNode,JsonNode)：Jackson 的 IntNode(1) 与 LongNode(1) 不 equals，
            // 而契约要的是"数字按值相等"（见项目里既有的 JsonEquivalence）
            Iterator<String> fields = v.fieldNames();
            while (fields.hasNext()) {
                String f = fields.next();
                assertTrue(JsonEquivalence.equivalent(v.get(f), m.get(f)),
                        "第 " + i + " 行字段 " + f + " 不一致：vendor=" + v.get(f) + " mine=" + m.get(f));
            }
        }
    }

    private static SoftwareListEntity entityFromVendor(JsonNode vendor) {
        JsonNode data = vendor.get("data");
        List<SoftwareMessageEntity> list = new ArrayList<>();
        for (JsonNode it : data.get("softwareList")) {
            List<RelationRoleVO> roles = null;
            if (it.get("roleList") != null && it.get("roleList").isArray()) {
                roles = new ArrayList<>();
                for (JsonNode r : it.get("roleList")) {
                    roles.add(RelationRoleVO.builder()
                            .id(text(r, "id")).name(text(r, "name")).key(text(r, "key")).build());
                }
            }
            list.add(SoftwareMessageEntity.builder()
                    .software(SoftwareEntity.builder()
                            .id(text(it, "id")).key(text(it, "key")).name(text(it, "name"))
                            .appName(text(it, "appName")).routerKey(text(it, "routerKey"))
                            .type(text(it, "type")).version(text(it, "version"))
                            .createdAt(num(it, "createdAt")).updatedAt(num(it, "updatedAt"))
                            .framework(text(it, "framework")).execName(text(it, "execName"))
                            .location(text(it, "location")).remoteIp(text(it, "remoteIp"))
                            .remoteName(text(it, "remoteName")).remotePassword(text(it, "remotePassword"))
                            .activeCreate(num(it, "activeCreate")).activeExpire(num(it, "activeExpire"))
                            .orderTop(it.get("orderTop").isNull() ? null : it.get("orderTop").asInt())
                            .orderTime(num(it, "orderTime")).desc(text(it, "desc"))
                            .activeCode(text(it, "activeCode")).screenshot(text(it, "screenshot"))
                            .icon(text(it, "icon"))
                            .build())
                    .releaseStatus(it.get("releaseStatus").isNull() ? null : it.get("releaseStatus").asInt())
                    .roleList(roles)
                    .web(it.get("web").isNull() ? null : it.get("web").asInt())
                    .url(text(it, "url"))
                    .build());
        }
        return SoftwareListEntity.builder().softwareList(list).total(data.get("total").asLong()).build();
    }

    private static Long num(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static JsonNode load(String resource) throws Exception {
        try (InputStream in = SoftwareListAssemblerTest.class.getResourceAsStream(resource)) {
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
