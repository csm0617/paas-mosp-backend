package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 厂商 GET /v1/service → data.services[] 的单元素形状。
 * <p><b>19 个键一个都不能少</b>；即使本原型填不出来的键也要以 null 出现，否则前端可能直接读空。
 * 键清单来自运行时样本：docs/findings/runtime-samples/service-list.json。
 */
@Data
@Builder
public class ServiceItemDTO {
    private String nameFr;
    private String nameUs;
    /** K8s Service 原样快照（前端会读 spec.ports）。 */
    private Object service;
    private List<Object> volumeTypeList;
    /** K8s Deployment 原样快照。 */
    private Object deployment;
    private Object serviceAuth;
    private Boolean terminated;
    private String hostIP;
    private Object nodePortService;
    private Object imageInfo;
    private Object mappingInfo;
    private List<String> softwareNameList;
    private Integer restartCount;
    private Object podInfoList;
    private Object belongNodeList;
    private Integer cpuOrder;
    private Integer memoryOrder;
    private Integer gpuOrder;
    private String fixedName;
}
