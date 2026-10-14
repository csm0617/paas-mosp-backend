package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 服务的映射信息（厂商 `ServiceMappingInfo`），只被 `GET /v1/software/relationService/{id}` 填充
 * （`GET /v1/service` 列表端点恒返回三键全 null 的**空对象**，见 `ServiceListAssembler`）。
 *
 * <p>厂商出处：`SoftwareService.getServiceMappingInfo:2974-3048`。规格见 `docs/findings/120`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceMappingInfoVO {

    private String serviceName;

    /** 内网访问：Service 的每个 port ⇒ `{containerPort: port.port, intranetPort: "<serviceName>:<port>"}`。 */
    private List<IntranetInfoVO> intranetInfo;

    /**
     * NodePort 访问：Service 里 `nodePort` 非空的 port + `<serviceName>-nodeport` 这个 Service 的**全部** port。
     */
    private List<NodePortInfoVO> nodePortInfo;

    /**
     * 负载均衡入口：厂商读 `servicesItem.getService().getProxy()`，而那个字段
     * （`IoK8sApiCoreV1Service.proxy`）**全仓库没有任何一处赋值** ⇒ **恒为 null** ⇒ 这个列表**恒空**。
     * 我们照抄"恒空"，不去编造。
     */
    private List<IngressInfoVO> ingressInfo;

    /** 厂商 `IntranetInfo`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IntranetInfoVO {
        private Integer containerPort;
        private String intranetPort;
    }

    /** 厂商 `NodePortInfo`。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NodePortInfoVO {
        private Integer containerPort;
        private Integer nodePort;
        private String protocol;
    }

    /** 厂商 `IngressInfo`（恒不使用，保留形状）。 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IngressInfoVO {
        private Integer containerPort;
        private String ingressPort;
        private String protocol;
    }
}
