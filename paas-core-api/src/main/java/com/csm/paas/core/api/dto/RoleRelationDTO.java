package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `POST /v1/user/userToRole` 的请求体（`RoleRelationInfo`）：`{userId, roleIdList}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleRelationDTO {

    private String userId;
    private List<String> roleIdList;
}
