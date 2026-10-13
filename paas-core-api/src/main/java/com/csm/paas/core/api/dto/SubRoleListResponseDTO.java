package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `GET /v1/sub_role/switchRole` 响应体（`SubRoleList`）：`{list,total}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleListResponseDTO {

    private List<SubRoleItemDTO> list;
    private long total;
}
