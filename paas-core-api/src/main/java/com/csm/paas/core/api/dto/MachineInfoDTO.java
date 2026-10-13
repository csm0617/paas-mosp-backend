package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

/** 厂商 GET /v1/licence 的 data 形状：{code, existGis}。 */
@Data
@Builder
public class MachineInfoDTO {
    private String code;
    private Boolean existGis;
}
