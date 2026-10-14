package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `UserIdsForm`（`POST /v1/sub_role/roleAddUser/{roleId}` 的请求体）：`{userIdList}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserIdsDTO {

    private List<String> userIdList;
}
