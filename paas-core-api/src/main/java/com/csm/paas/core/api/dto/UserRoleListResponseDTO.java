package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `UserRoleList`：`{list,total}`（`GET /v1/user/list` 的外层）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleListResponseDTO {

    private List<UserRolesItemDTO> list;
    private long total;
}
