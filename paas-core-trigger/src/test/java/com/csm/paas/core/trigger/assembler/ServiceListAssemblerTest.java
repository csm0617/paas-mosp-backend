package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.ServiceListResponseDTO;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
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
 * 离线契约回放：用**录制下来的厂商响应**喂给装配器，断言
 * ① 形状（信封键 / data 键 / 项键集合）与厂商逐字一致；
 * ② `read-paths.json` 里声明的每一条"前端实际读取路径"都成立。
 * <p>不需要集群、不需要厂商后端——这是"厂商前端一行不改"门槛的可离线部分。
 */
class ServiceListAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void envelopeAndItemShapeMatchRecordedVendorResponse() throws Exception {
        JsonNode vendor = load("/vendor/v1_service.json");
        ResponseDTO<ServiceListResponseDTO> response = ResponseDTO.ok(
                ServiceListAssembler.toResponse(entitiesFromVendor(vendor)));
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(response);

        assertEquals(keys(vendor), keys(mine), "信封键必须一致");
        assertEquals(keys(vendor.get("data")), keys(mine.get("data")), "data 键必须一致");

        Set<String> vendorItemKeys = keys(vendor.get("data").get("services").get(0));
        Set<String> mineItemKeys = keys(mine.get("data").get("services").get(0));
        assertEquals(19, mineItemKeys.size(), "厂商就是 19 个键");
        assertEquals(vendorItemKeys, mineItemKeys, "项键集合必须一个不缺一个不多");
    }

    @Test
    void everyDeclaredFrontendReadPathHolds() throws Exception {
        JsonNode vendor = load("/vendor/v1_service.json");
        JsonNode readPaths = load("/vendor/read-paths.json").get("GET /v1/service").get("readPaths");
        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(ResponseDTO.ok(
                ServiceListAssembler.toResponse(entitiesFromVendor(vendor))));

        List<String> checked = new ArrayList<>();
        List<String> deviations = new ArrayList<>();
        Set<String> declaredDeviations = new LinkedHashSet<>();
        for (JsonNode p : readPaths) {
            if (p.has("allowDeviation")) {
                declaredDeviations.add(p.get("path").asText());
            }
        }
        for (JsonNode p : readPaths) {
            String path = p.get("path").asText();
            List<JsonNode> vendorValues = resolve(vendor, path);
            List<JsonNode> mineValues = resolve(mine, path);
            if (p.has("mode") && "container".equals(p.get("mode").asText())) {
                assertTrue(!mineValues.isEmpty(), "容器型路径必须存在: " + path);
            } else if (vendorValues.isEmpty()) {
                continue; // 厂商本就没有（例如 serviceAuth 为 null 时的 .auth）
            } else if (p.has("allowDeviation")) {
                // 已记录的允许偏离：按声明的"偏离方向"校验，且偏离条数受控（新增偏离会被下面的集合断言拦住）
                String kind = p.path("deviationKind").asText();
                if ("at-least-as-complete".equals(kind)) {
                    assertTrue(mineValues.stream().noneMatch(JsonNode::isNull),
                            "偏离方向声明为'我们更完整'，但重写版出现 null: " + path);
                } else if ("display-equivalent".equals(kind)) {
                    assertEquals(vendorValues.size(), mineValues.size(), "项数必须一致: " + path);
                    for (int i = 0; i < vendorValues.size(); i++) {
                        JsonNode v = vendorValues.get(i);
                        JsonNode m = mineValues.get(i);
                        boolean equivalent = v.equals(m)
                                || (renderAsZero(v) && renderAsZero(m));
                        assertTrue(equivalent, "声明为显示等价，但差异明显: " + path + " vendor=" + v + " mine=" + m);
                    }
                } else {
                    throw new AssertionError("allowDeviation 缺少 deviationKind: " + path);
                }
                deviations.add(path);
                continue;
            } else {
                assertEquals(vendorValues, mineValues, "读取路径值必须一致: " + path);
            }
            checked.add(path);
        }
        assertTrue(checked.size() >= 9, "至少应校验 9 条读取路径，实际 " + checked.size());
        assertEquals(declaredDeviations, new LinkedHashSet<>(deviations),
                "允许偏离的集合必须与 read-paths.json 声明的一致——新增偏离会在这里被拦住");
    }

    @Test
    void namedTargetPortSurvivesAssembly() throws Exception {
        JsonNode vendor = load("/vendor/v1_service.json");
        // 把第一个服务的 targetPort 改成具名端口（集群里出现过的形态）
        ObjectNode first = (ObjectNode) vendor.get("data").get("services").get(0);
        ObjectNode ports = (ObjectNode) first.get("service").get("spec").get("ports").get(0);
        ports.put("targetPort", "http");

        ObjectNode mine = (ObjectNode) MAPPER.valueToTree(ResponseDTO.ok(
                ServiceListAssembler.toResponse(entitiesFromVendor(vendor))));

        assertEquals("http",
                mine.get("data").get("services").get(0).get("service").get("spec").get("ports").get(0)
                        .get("targetPort").asText(),
                "具名端口不能在装配环节被数字类型吃掉");
    }

    /** 前端的 `a.restartCount || 0` 语义：null 与 0 渲染一致。 */
    private static boolean renderAsZero(JsonNode node) {
        return node.isNull() || (node.isNumber() && node.asInt() == 0);
    }

    // ---------- helpers ----------

    private static JsonNode load(String resource) throws Exception {
        try (InputStream in = ServiceListAssemblerTest.class.getResourceAsStream(resource)) {
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

    /** 用厂商样本"回放"成领域实体：等价于 Port 从集群读到同样的数据。 */
    @SuppressWarnings("unchecked")
    private static ServiceListEntity entitiesFromVendor(JsonNode vendor) {
        JsonNode data = vendor.get("data");
        List<ServiceRuntimeEntity> services = new ArrayList<>();
        for (JsonNode item : data.get("services")) {
            services.add(ServiceRuntimeEntity.builder()
                    .name(item.get("deployment").get("metadata").get("name").asText())
                    .namespace("gaop-namespace")
                    .serviceSnapshot(MAPPER.convertValue(item.get("service"), java.util.Map.class))
                    .deploymentSnapshot(MAPPER.convertValue(item.get("deployment"), java.util.Map.class))
                    .restartCount(item.get("restartCount").isNull() ? null : item.get("restartCount").asInt())
                    .replicas(item.get("deployment").get("spec").get("replicas").asInt())
                    .build());
        }
        int running = (int) services.stream().filter(s -> s.getReplicas() > 0).count();
        return ServiceListEntity.builder()
                .runningDeployment(running)
                .total(services.size())
                .count(services.size())
                .services(services)
                .build();
    }

    /** 支持 `a.b` 与 `a.b[]` / `a.b[].c` 的路径解析（与 tools/contract-check.py 同语义）。 */
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
