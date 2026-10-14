package com.csm.paas.core.domain.node.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单节点磁盘总量；照抄厂商 `controller/response/NodeDiskInfo.java:8-12`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodeDiskVO {

    private String name;
    private Long size;
}
