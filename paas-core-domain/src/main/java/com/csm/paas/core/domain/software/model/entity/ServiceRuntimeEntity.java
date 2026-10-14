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

    /** 工作负载标签 `system/appType`（厂商 `GET /v1/service/gaop` 的过滤依据，可能为 null）。 */
    private String appType;

    /** 工作负载标签 `system/appName`（厂商 `GET /v1/service/gaop` 与 `serviceAuth` 都靠它判归属）。 */
    private String appName;

    /**
     * `GET /v1/service/gaop` 专用的显示名（厂商 `e.setFixedName(GaopName.gaopName.get(name))`）。
     * <p>**只在这个端点里赋值**；为空时装配器回退服务名，从而不影响已对拉通过的 `/v1/service` 列表行为。
     */
    private String fixedName;

    /**
     * 工作负载 pod 模板上的注解 `gaop/serviceLabel`（= 用户在界面上设置的**服务显示名**）。
     * <p>两个用途：厂商 `GET /v1/service/gaop` 的 keyword **兜底匹配**就打在它上面
     * （`ServiceController.filterServiceName:2070-2081`，**大小写敏感的 contains**）；
     * `PUT /v1/service/updateName` 改的也是这个注解。
     */
    private String serviceLabel;

    /**
     * 注解 `gaop/serviceType`（= 「前台服务」/「后台服务」），厂商 `GET /v1/service/serviceAuth`
     * 在"该服务没有授权记录"时用它决定要不要保留（`ServiceController:3522-3555`）。
     */
    private String serviceType;
    private Map<String, Object> serviceSnapshot;
    private Map<String, Object> deploymentSnapshot;

    /**
     * 服务映射信息；**只有 `GET /v1/software/relationService/{id}` 会填**，
     * `GET /v1/service` 列表端点保持 null（列表端点恒返回三键全 null 的空对象，已 A/B 对拉通过）。
     */
    private com.csm.paas.core.domain.software.model.valobj.ServiceMappingInfoVO mappingInfo;

    // ---------- 以下三个字段**只有 `GET /v1/service/{serviceName}`（服务详情）会填** ----------

    /**
     * 容器上次终止状态的原样快照（厂商 `getServiceDetail:1413-1431` 的 `V1ContainerStateTerminated`）。
     * 列表端点不填 ⇒ null。
     */
    private Map<String, Object> terminatedSnapshot;

    /**
     * `<serviceName>-nodeport` 这个 Service 的**原样快照**（厂商 `getServiceDetail:1439-1468`）。
     * 读不到 ⇒ null（厂商 catch 掉 ApiException 后把 `nodePortService` 设成 null）。
     * <p>注意与 {@link #serviceSnapshot} 不是同一个对象：那个是**同名** Service，这个是 `-nodeport` 后缀那个。
     */
    private Map<String, Object> nodePortServiceSnapshot;

    /**
     * 详情端点算出的英文名（厂商 `getServiceDetail:1476-1477`）：
     * `i18nUtil.getAutoNameMessage("service","en_US",key)`，**空白则回退**注解 `gaop/serviceLabel`。
     */
    private String nameUs;

    /** 同上，`fr_FR`（厂商 `:1478-1479`）。 */
    private String nameFr;
}
