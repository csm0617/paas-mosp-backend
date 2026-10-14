package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `SoftwareIcon`（`PUT /v1/software/update_icon` 的请求体）。
 * <p>`icon` 为 **null** 表示"清空图标"（照抄厂商的第三分支）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareIconDTO {

    private String icon;
}
