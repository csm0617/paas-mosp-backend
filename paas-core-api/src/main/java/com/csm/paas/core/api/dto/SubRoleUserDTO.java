package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 子角色关联用户（厂商 `SubRoleUser`）：`{userId,userName}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleUserDTO {

    private String userId;
    private String userName;
}
