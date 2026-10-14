package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 厂商 `NodeService`（`controller/response/NodeService.java`）：`{ports, type}`。
 *
 * <p>只用在一个地方：`GET /v1/service/{serviceName}` 详情里的 `nodePortService`
 * （厂商 `ServiceController.java:1439-1468`，读 `<serviceName>-nodeport` 这个 Service）。
 * 厂商对端口只做**裁剪**（不是整份 K8s 快照），所以我们这里也手工组装：
 * `name`/`protocol`/`port`/`targetPort`/`appProtocol`/`nodePort`。
 */
@Data
@Builder
public class NodeServiceDTO {

    private List<NodeServicePortDTO> ports;
    private String type;

    /**
     * 厂商 `NodeServicePort`。两处照抄点：
     * <ol>
     *   <li>`targetPort` 在厂商那边是 `IntOrString.getIntValue()`——具名端口会得到 **null**
     *       （`getIntValue()` 对 string 形态返回 null，不抛异常）；</li>
     *   <li>端口列表为空时 `ports` 是**空数组**（不是 null），而 Service 整个读不到时
     *       `nodePortService` 是 **null**（两者不同）。</li>
     * </ol>
     */
    @Data
    @Builder
    public static class NodeServicePortDTO {
        private String name;
        private String protocol;
        private Integer port;
        private Integer targetPort;
        private String appProtocol;
        private Integer nodePort;
    }
}
