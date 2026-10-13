package com.csm.paas.core.cases.role;

import java.util.List;

/** 删除战位（含 6 表级联）的用例。 */
public interface IRoleDeleteCase {

    /**
     * 批量删除战位，语义与厂商 `RoleService.deleteRoleRelation`（`RoleService.java:707-730`）一致：
     * <ol>
     *   <li>先按入参顺序**逐条校验**（不存在 → `role.missing.storage`；`admin`/`dev` → `role.cannot.delete.storage`）；</li>
     *   <li>再按厂商固定的 6 张表顺序删除，**整个用例一个事务**——任一条失败全部回滚。</li>
     * </ol>
     *
     * @param ids 战位 id 列表（来自 `DELETE /v1/role?roleId=a,b,c`）
     */
    void deleteRoles(List<String> ids);
}
