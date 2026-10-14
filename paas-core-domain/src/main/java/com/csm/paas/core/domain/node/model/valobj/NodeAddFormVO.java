package com.csm.paas.core.domain.node.model.valobj;

import lombok.Builder;
import lombok.Data;

/**
 * 新增节点表单；照抄厂商 `controller/response/AddNodeByInit.java:8-16`（`labelNvidia` 默认 false）。
 */
@Data
@Builder
public class NodeAddFormVO {

    private String ip;
    private String hostname;
    @Builder.Default
    private Boolean labelNvidia = false;
    private String netWorkInterface;
}
