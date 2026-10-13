package com.csm.paas.core.cases.role;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleFormVO;

/** 新增 / 修改战位的用例。 */
public interface IRoleWriteCase {

    /**
     * 新增战位（`POST /v1/role`）。校验在领域层（`IRoleCommandService.prepareNewRole`），
     * **插行 + 关系写入在一个事务里**。
     *
     * <p>关系写入照抄厂商 `RoleService.addRole`（`:554-606`）：三个列表都是"先按名字/ id 反查、
     * 找不到就跳过"，再调对应的 relate（relate 内部是**先删该战位的旧关联再插入**）。
     * <ul>
     *   <li>`userList` 是**用户名**列表 → 反查 `g_user.id`；<b>若新战位的 key 是 `admin`，
     *       厂商会强制把 `admin` 用户也加进去</b>（`usersRelateRole`，`:640-642`）；</li>
     *   <li>`subRoleIdList` 是子角色 **id** 列表 → 校验存在性；</li>
     *   <li>`softwareList` 是**软件名**列表 → 需要 `g_software`，**属 K2 域，本轮未实现**
     *       （席位页的"关联应用"对话框本身就依赖 K2 的 `/v1/software`，见地图上的欠账）。</li>
     * </ul>
     */
    RoleEntity addRole(RoleFormVO form);

    /**
     * 修改战位（`PUT /v1/role/{id}`）：只更新战位行，**不动关系**
     * （厂商 `RoleService.editRole`，`:692-705` 只做 selective update；关系由别的端点管）。
     */
    RoleEntity editRole(String id, RoleFormVO form);
}
