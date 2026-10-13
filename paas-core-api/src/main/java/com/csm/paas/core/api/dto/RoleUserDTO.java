package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 战位关联用户（厂商 `RoleUser`）：`{userId,userName}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleUserDTO {

    private String userId;
    private String userName;
}
