package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 厂商 `GET /v1/service/{serviceName}` 的响应形状（厂商类
 * `third/response/tenx/v2/services/list/ServicesItem`）。
 *
 * <p>厂商出处：`ServiceController.java:1404-1481`（`getServiceDetail`）。
 * 与 {@link ServiceItemDTO}（列表项 19 键）**字段集不同**：多出 `terminated`/`hostIP`/`nodePortService`，
 * 且 `cpuOrder`/`memoryOrder`/`gpuOrder` 是 **Double 且字段初始化器默认 `0.0`**（列表项那边是 Integer 且为 null）。
 * 因此这里单独开一个 DTO，**不去动** `ServiceItemDTO`——后者已被 `ServiceListAssemblerTest`
 * 用录制的厂商样本钉死为"恰好 19 键"。
 *
 * <p>键顺序照厂商声明顺序（`terminated` 紧跟 `serviceAuth`）。厂商那几个 `@JsonProperty`
 * 只写在字段上（没有对应的 getter 注解），Jackson 因此按 **getter 的声明顺序**输出；
 * 这个端点没有录制样本可对拉，键顺序按源码声明顺序近似。
 */
@Data
@Builder
public class ServiceDetailDTO {

    private Object service;
    private List<Object> volumeTypeList;
    private Object deployment;
    private Object serviceAuth;
    /** 容器上次终止状态（厂商 `V1ContainerStateTerminated` 原样对象）；没找到相关 pod ⇒ null。 */
    private Object terminated;
    /** 匹配到的 pod 的 `status.hostIP`。 */
    private String hostIP;
    /** `<serviceName>-nodeport` 这个 Service 的裁剪形状；不存在 ⇒ null。 */
    private NodeServiceDTO nodePortService;
    /** `deployment.spec.template.spec.containers[0].image`。 */
    private String imageInfo;
    private Object mappingInfo;
    private List<String> softwareNameList;
    private Integer restartCount;
    private Object podInfoList;
    private Object belongNodeList;
    /** 单位 m；厂商字段初始化器默认 `0.0`。 */
    private Double cpuOrder;
    /** 单位 MiB；厂商字段初始化器默认 `0.0`。 */
    private Double memoryOrder;
    /** `nvidia.com/gpu`；厂商字段初始化器默认 `0.0`。 */
    private Double gpuOrder;
    private String fixedName;
    private String nameFr;
    private String nameUs;
}
