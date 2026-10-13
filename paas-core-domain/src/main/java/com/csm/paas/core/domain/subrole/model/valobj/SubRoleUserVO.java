package com.csm.paas.core.domain.subrole.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 子角色关联的用户（厂商 `SubRoleUser`，构造器参数序为 (userId, userName)）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleUserVO {

    private String userId;
    private String userName;
}
