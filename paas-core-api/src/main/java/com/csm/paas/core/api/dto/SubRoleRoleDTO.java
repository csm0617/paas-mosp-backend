package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 子角色关联战位（厂商 `SubRoleRoleForm`）：`{roleId,roleKey,roleName}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubRoleRoleDTO {

    private String roleId;
    private String roleKey;
    private String roleName;
}
