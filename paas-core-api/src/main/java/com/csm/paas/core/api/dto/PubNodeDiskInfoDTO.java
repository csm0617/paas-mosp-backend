package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单节点磁盘总量。
 * <p>照抄厂商 {@code controller/response/NodeDiskInfo.java:8-12}（{@code name}/{@code size}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubNodeDiskInfoDTO {

    private String name;
    private Long size;
}
