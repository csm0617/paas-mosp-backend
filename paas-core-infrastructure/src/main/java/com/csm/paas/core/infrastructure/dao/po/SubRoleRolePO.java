package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** 子角色关联战位的投影（厂商 `SubRoleRoleForm(roleId, roleKey, roleName)`）。 */
@Data
public class SubRoleRolePO {

    private String roleId;
    private String roleKey;
    private String roleName;
}
