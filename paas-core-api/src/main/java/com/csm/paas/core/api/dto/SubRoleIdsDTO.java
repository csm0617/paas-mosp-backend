package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `SubRoleIdsForm`（`DELETE /v1/sub_role/delete` 的请求体）：`{subRoleIdList}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleIdsDTO {

    private List<String> subRoleIdList;
}
