package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 网卡信息（{@code GET /v1/pub/vip/interfaces} 的响应元素）。
 * <p>照抄厂商 {@code service/vip/InterfaceInfo.java:10-21}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubInterfaceInfoDTO {

    private Integer index;
    private String name;
    private String hardwareAddr;
    private String flags;
    private List<String> addresses;
}
