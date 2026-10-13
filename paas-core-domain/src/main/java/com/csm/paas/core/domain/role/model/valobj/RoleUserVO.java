package com.csm.paas.core.domain.role.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 战位关联的用户（厂商 `RoleUser`：`{userId,userName}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleUserVO {

    private String userId;
    private String userName;
}
