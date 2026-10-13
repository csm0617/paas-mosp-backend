package com.csm.paas.core.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 厂商 GET /v1/role → data 形状：{list, total}。 */
@Data
@Builder
public class RoleListResponseDTO {
    private List<RoleItemDTO> list;
    private long total;
}
