package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 网卡名 + 该网卡下的 ip 列表。
 * <p>照抄厂商 {@code controller/response/NetNameCoverIpInfo.java:8-10}（字段名 {@code netName}/{@code ipList}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubNodeNetNameIpDTO {

    private String netName;
    private List<String> ipList;
}
