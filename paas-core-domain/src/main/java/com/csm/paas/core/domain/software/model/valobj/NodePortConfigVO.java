package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 入参里的一个端口配置（厂商 `NodePortConfig`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodePortConfigVO {

    private int port;
    private String protocol;
    private Integer nodePort;
}
