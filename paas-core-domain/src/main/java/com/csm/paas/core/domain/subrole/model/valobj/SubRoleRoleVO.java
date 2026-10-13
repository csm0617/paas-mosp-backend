package com.csm.paas.core.domain.subrole.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 子角色关联的战位（厂商 `SubRoleRoleForm`，构造器参数序为 (roleId, roleKey, roleName)）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleRoleVO {

    private String roleId;
    private String roleKey;
    private String roleName;
}
