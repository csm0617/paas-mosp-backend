package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 软件授权列表项（厂商 `SoftwareAuthInfo`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareAuthInfoVO {

    private String id;
    private String softWareName;
    private String softWareKey;
    private String framework;
    private Integer status;
    private Long authCreateTime;
    private Long authExpireTime;
}
