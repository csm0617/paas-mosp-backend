package com.csm.paas.core.domain.subrole.service;

import java.util.List;

/**
 * 子角色写操作（厂商 `SubRoleController` 的 `PUT /update`、`DELETE /delete`、
 * `POST /userAddRole/{userId}`、`POST /roleAddUser/{roleId}`）。
 */
public interface ISubRoleCommandService {

    /**
     * 按 id 集合删除子角色（厂商 `DELETE /v1/sub_role/delete` → `SubRoleServiceImpl.deleteByIds:277-291`）。
     *
     * <p>逐个 id（**不校验存在性、也不报错**）：
     * <ol>
     *   <li>若该子角色存在且 `key` 非空白 ⇒ 删它的 i18n 自动名（**本环境未实现 i18n 覆盖机制**，故跳过）；</li>
     *   <li>删 `g_sub_role_role` 里该子角色的关联；</li>
     *   <li>删 `g_sub_role` 行；</li>
     *   <li>删 `g_user_sub_role` 里该子角色的关联。</li>
     * </ol>
     * **恒返回 true**。
     */
    boolean deleteSubRoles(List<String> subRoleIds);
}
