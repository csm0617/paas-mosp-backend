package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * `PUT /v1/service/patch` 的请求体（厂商 `third/response/tenx/v2/services/list/ServicesItem` 的
 * 三个 K8s 快照字段）。
 *
 * <p>厂商出处：`ServiceController.updateServiceConfig:1165-1402`。
 *
 * <p><b>为什么用 Map</b>：厂商请求体里 `deployment` 是时速云的扩展组 `IoK8sApiExtensionsV1beta1Deployment`、
 * `service` 是 `IoK8sApiCoreV1Service`、`nodePortService` 是裁剪过的 `{ports,type}`。
 * 本平台不引那两套模型类，按仓库既有口径（`ServiceItemDTO.deployment` 等）**收原样 JSON 快照**，
 * 由领域层按 apps/v1 口径规范化后再写回集群。
 *
 * <p><b>破坏性</b>：会 replace Deployment、patch/创建/删除 `<name>-nodeport` Service、patch 主 Service。
 * 照抄厂商代码，**未做在线实验**（见 `IServiceQueryService.updateServiceConfig` 的类 C 说明）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServicePatchDTO {

    /** 厂商 `item.getService()`（主 Service，读不到 ⇒ 400 `k8s.get.service.failed`）。 */
    private Map<String, Object> service;

    /** 厂商 `item.getDeployment()`（`IoK8sApiExtensionsV1beta1Deployment` 原样快照）。 */
    private Map<String, Object> deployment;

    /** 厂商 `item.getNodePortService()`：`{ports:[{name,protocol,port,targetPort,appProtocol,nodePort}], type}`。 */
    private Map<String, Object> nodePortService;
}
