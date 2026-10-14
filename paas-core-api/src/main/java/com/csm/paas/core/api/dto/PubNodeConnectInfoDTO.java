package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新增节点 ip 连通性测试结果。
 * <p>照抄厂商 {@code controller/response/ConnectInfo.java:10-16}
 * （{@code connect}/{@code hostname}/{@code netWorkInfo}，注意大小写 {@code netWorkInfo}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubNodeConnectInfoDTO {

    private Boolean connect;
    private String hostname;
    private List<PubNodeNetNameIpDTO> netWorkInfo;
}
