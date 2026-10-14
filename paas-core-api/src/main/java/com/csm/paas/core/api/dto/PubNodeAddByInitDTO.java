package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增节点的三步表单（initHost / setDockerConfig / finallyAddNode 共用同一个 body）。
 * <p>照抄厂商 {@code controller/response/AddNodeByInit.java:8-16}：
 * {@code labelNvidia} 默认 {@code false}（字段默认值也要照抄，因为厂商会 {@code Boolean.TRUE.equals(...)}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubNodeAddByInitDTO {

    private String ip;
    private String hostname;
    @Builder.Default
    private Boolean labelNvidia = false;
    private String netWorkInterface;
}
