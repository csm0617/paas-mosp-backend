package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 厂商 `SoftwareAuthInfo`（7 键）：软件授权列表项。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareAuthInfoDTO {

    private String id;
    private String softWareName;
    private String softWareKey;
    private String framework;
    /** 0=未授权 1=已授权 2=已过期。 */
    private Integer status;
    private Long authCreateTime;
    private Long authExpireTime;
}
