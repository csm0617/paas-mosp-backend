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

    /**
     * 新增子角色（厂商 `POST /v1/sub_role` → `SubRoleController.addRole:59-70` +
     * `SubRoleServiceImpl.addRole:113-140`，规格见 `docs/findings/148`）。
     *
     * <p>顺序：控制器先做**重名检查**——`existKeyName(key, name)` 查的是 **`g_role`（战位表）**，
     * 命中 ⇒ 400 `sub.role.storage.existed`（带 key、name）。
     * 然后：`name` 为空或 >100 ⇒ 400 `sub.role.name.invalid.length`；`key` 为空或 >36 ⇒
     * 400 `sub.role.unique.identifier.invalid.length`；`desc` 空白 ⇒ 存 **null**；`desc` >255 ⇒
     * 400 `sub.role.desc.invalid.length`；`id = UUID`、`createdAt = updatedAt = now`；插入；
     * `userList` 非空 ⇒ 去重后逐个关联（**用户不存在则跳过**）。
     *
     * @return 新增后的子角色（再查一次库）
     */
    com.csm.paas.core.domain.subrole.model.entity.SubRoleEntity addSubRole(
            com.csm.paas.core.domain.subrole.model.valobj.SubRoleFormVO form);

    /**
     * 修改子角色（厂商 `PUT /v1/sub_role/update` → `SubRoleServiceImpl.updateSuRole:233-260`）。
     *
     * <p>顺序：按 id 取（**不存在 ⇒ 400 `sub.role.storage.not.found`**，见 B24）；
     * `key` 与别的子角色重复 ⇒ 400 `sub.role.key.exist`；`name` 与别的子角色重复 ⇒
     * 400 `sub.role.change.name.exist`；`name`/`key` 非 null 时校验长度（>100 / >36 或 <1）⇒
     * 两个 400；`desc` 空白 ⇒ null、>255 ⇒ 400；`updatedAt = now`；
     * **先删光该子角色的用户关联再按 `userList` 去重关联**；最后选择性更新（只写非 null 字段）。
     *
     * @return 受影响行数 > 0
     */
    boolean updateSubRole(com.csm.paas.core.domain.subrole.model.valobj.SubRoleFormVO form);
}
