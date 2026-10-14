package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VIP 条目（{@code GET/POST/DELETE /v1/pub/vip} 的响应元素）。
 * <p>照抄厂商 {@code controller/response/VipInfo.java:8-12}。
 * <p>注意厂商读回来时会把 {@code ip/掩码} 截成纯 ip（{@code PubVipController.getVipInfos:96-100}），
 * 但**写入**时固定补 {@code /32}（{@code addVip:111}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PubVipInfoDTO {

    private String ip;
    private String netName;
}
