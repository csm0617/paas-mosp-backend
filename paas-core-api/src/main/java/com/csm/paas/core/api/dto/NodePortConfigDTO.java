package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `NodePortConfig`：`{port, protocol, nodePort}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodePortConfigDTO {

    private int port;
    private String protocol;
    private Integer nodePort;
}
