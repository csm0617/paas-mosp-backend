package com.csm.paas.core.domain.software.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 一个运行中的服务（= 一对 K8s Service / Deployment）。
 * <p>{@code targetPort} 刻意保留**字符串**形态：具名端口（{@code http}）与数字端口都能无损表达，
 * 这正是厂商后端（client-java 18.0.1，Integer 模型）会崩掉的地方。
 * <p>{@code serviceSnapshot} / {@code deploymentSnapshot} 是厂商契约需要的原样 K8s 对象快照
 * —— 原型的刻意妥协：兼容性由 ACL 保住，领域只消费它需要的字段。
 */
@Data
@Builder
public class ServiceRuntimeEntity {

    private String name;
    private String namespace;
    private String type;
    private String portName;
    private Integer port;
    private String targetPort;
    private String protocol;
    private Integer replicas;
    private Integer readyReplicas;
    private Integer restartCount;
    /** 工作负载的容器镜像（厂商 `imageInfo`，前端"镜像"列直接显示）。 */
    private String image;
    private String hostIP;
    private List<Integer> nodePorts;
    private String createdAt;
    /** 授权状态（来自 g_service_auth；厂商侧为空时前端会显示"认证失败"）。 */
    private ServiceAuthEntity serviceAuth;
    private Map<String, Object> serviceSnapshot;
    private Map<String, Object> deploymentSnapshot;
}
