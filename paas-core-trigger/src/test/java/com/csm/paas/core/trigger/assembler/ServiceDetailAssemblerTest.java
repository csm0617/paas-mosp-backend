package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.api.dto.ServiceDetailDTO;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `GET /v1/service/{serviceName}` 的装配契约（厂商 `ServicesItem` 形状，
 * `ServiceController:1404-1481`）。
 *
 * <p>重点：三个 `*Order` 是 **Double 且恒为 0.0**、`restartCount` 恒为 **0**（厂商详情端点从不赋值），
 * 以及 `nodePortService` 的"读不到 ⇒ null / 读到但无端口 ⇒ 空数组"两种情形。
 */
class ServiceDetailAssemblerTest {

    @Test
    void detailDefaultsMatchVendorFieldInitializers() {
        var entity = ServiceRuntimeEntity.builder()
                .name("gaop-web")
                .image("harbor/gaop-web:1.0")
                .nameUs("Web")
                .nameFr("Web-fr")
                .build();

        ServiceDetailDTO dto = ServiceListAssembler.toDetailResponse(entity);

        assertEquals("harbor/gaop-web:1.0", dto.getImageInfo());
        assertEquals("Web", dto.getNameUs());
        assertEquals("Web-fr", dto.getNameFr());
        assertEquals(0, dto.getRestartCount(), "厂商详情端点里 restartCount 恒为 0");
        assertEquals(0.0, dto.getCpuOrder());
        assertEquals(0.0, dto.getMemoryOrder());
        assertEquals(0.0, dto.getGpuOrder());
        assertNull(dto.getVolumeTypeList());
        assertNull(dto.getSoftwareNameList());
        assertNull(dto.getPodInfoList());
        assertNull(dto.getBelongNodeList());
        assertNotNull(dto.getMappingInfo(), "mappingInfo 是三键全 null 的空对象（与列表端点一致）");
    }

    /** `<name>-nodeport` 读不到 ⇒ `nodePortService` 为 **null**。 */
    @Test
    void missingNodePortServiceIsNull() {
        var entity = ServiceRuntimeEntity.builder().name("svc").build();

        assertNull(ServiceListAssembler.toDetailResponse(entity).getNodePortService());
    }

    /** 读到了 ⇒ 裁剪成 `{ports, type}`，端口只留 6 个字段。 */
    @Test
    void nodePortServiceIsTrimmedToSixPortFields() {
        var entity = ServiceRuntimeEntity.builder()
                .name("svc")
                .nodePortServiceSnapshot(Map.of(
                        "spec", Map.of(
                                "type", "NodePort",
                                "ports", List.of(port("http", "TCP", 80, 8080, "HTTP", 30080)))))
                .build();

        var nodeService = ServiceListAssembler.toDetailResponse(entity).getNodePortService();

        assertNotNull(nodeService);
        assertEquals("NodePort", nodeService.getType());
        assertEquals(1, nodeService.getPorts().size());
        var p = nodeService.getPorts().get(0);
        assertEquals("http", p.getName());
        assertEquals("TCP", p.getProtocol());
        assertEquals(80, p.getPort());
        assertEquals(8080, p.getTargetPort());
        assertEquals("HTTP", p.getAppProtocol());
        assertEquals(30080, p.getNodePort());
    }

    /**
     * 厂商 `targetPort.getIntValue()`：具名端口（字符串形态）得到 **null**（不抛异常）；
     * 数字形态得到该数字。
     */
    @Test
    void namedTargetPortBecomesNull() {
        var entity = ServiceRuntimeEntity.builder()
                .name("svc")
                .nodePortServiceSnapshot(Map.of(
                        "spec", Map.of(
                                "type", "NodePort",
                                "ports", List.of(
                                        Map.of("port", 80, "targetPort", "http"),
                                        Map.of("port", 81, "targetPort", 8081)))))
                .build();

        var ports = ServiceListAssembler.toDetailResponse(entity).getNodePortService().getPorts();

        assertNull(ports.get(0).getTargetPort(), "具名 targetPort ⇒ null");
        assertEquals(8081, ports.get(1).getTargetPort());
    }

    /** 读到了但没端口 ⇒ `ports` 是**空数组**（不是 null）。 */
    @Test
    void emptyPortsStayEmptyList() {
        var entity = ServiceRuntimeEntity.builder()
                .name("svc")
                .nodePortServiceSnapshot(Map.of("spec", Map.of("type", "ClusterIP")))
                .build();

        var nodeService = ServiceListAssembler.toDetailResponse(entity).getNodePortService();

        assertNotNull(nodeService.getPorts());
        assertTrue(nodeService.getPorts().isEmpty());
    }

    private static Map<String, Object> port(String name, String protocol, int port, Object targetPort,
                                           String appProtocol, int nodePort) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("protocol", protocol);
        map.put("port", port);
        map.put("targetPort", targetPort);
        map.put("appProtocol", appProtocol);
        map.put("nodePort", nodePort);
        return map;
    }
}
