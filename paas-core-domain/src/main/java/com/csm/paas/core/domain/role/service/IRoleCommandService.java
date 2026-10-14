package com.csm.paas.core.domain.role.service;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleFormVO;

/**
 * 战位写操作（新增 / 修改）的校验与构造——**厂商把这些规则散在 controller 与 service 里**
 * （`RoleController.java:186-251`、`RoleService.java:533-610`、`692-705`），这里收回领域层。
 *
 * <h3>新增（`POST /v1/role`）</h3>
 * <ul>
 *   <li>key 或 name 已存在 → `role.storage.existed`（**注意是 OR**：key 撞或 name 撞都拒）；</li>
 *   <li>name 长度 &gt;100 或 &lt;1 → `role.name.invalid.length`；</li>
 *   <li>key 长度 &gt;36 或 **&lt;0** → `role.unique.identifier.invalid.length`
 *       （厂商写的是 `&lt; 0`，所以**空 key 也能过**——照抄这个古怪上界）；</li>
 *   <li>desc 空白 → **null**（新增时）；&gt;255 → `role.desc.invalid.length`；</li>
 *   <li>`updatedAt` **不设置**（厂商 `addRole` 只设 `createdAt`，见 `RoleService.java:552`）。</li>
 * </ul>
 *
 * <h3>修改（`PUT /v1/role/{id}`）——保留字保护矩阵（逐条照抄 `RoleController.java:213-244`）</h3>
 * <ul>
 *   <li>角色不存在 → `role.storage.not.found`；</li>
 *   <li>**只要表单带了 `key`**（哪怕与当前 key 相同）且目标是 `admin` → `role.can.not.edit.admin.key`；
 *       目标是 `dev` → `role.can.not.edit.dev.key`；</li>
 *   <li>表单 `key` 是 `admin`/`dev` → `role.identifier.reserved.word`（带参数）；</li>
 *   <li>key 与他人重复 → `role.key.exist`；name 与他人重复 → `role.change.name.exist`；</li>
 *   <li>name 长度 &gt;100 或 &lt;1 → `role.change.name.invalid`；key 长度 &gt;36 或 &lt;1 → `role.change.key.name.invalid`；</li>
 *   <li>desc 空白 → **空串 `""`**（修改时；与新增的 null 不同）、&gt;255 → `role.change.desc.invalid`；</li>
 *   <li>`updatedAt` = now，`createdAt` 保留原值；字段为 null 时**不更新**（厂商用 selective update）。</li>
 * </ul>
 * <p><b>`cicd`/`root` 完全不受保护</b>：厂商的保留字常量里有它们，但这条路径只判 `admin`/`dev`
 * （与删除路径一致，见 `docs/findings/29` §4）。照抄，不"顺手修好"。
 */
public interface IRoleCommandService {

    /**
     * 删除前的逐条校验（厂商 `RoleService.deleteRoleRelation`，`RoleService.java:711-719`）：
     * id 不存在 → `role.missing.storage`；`key` 是 `admin`/`dev` → `role.cannot.delete.storage`。
     * <p>返回可删除的实体，顺序与入参一致。
     */
    java.util.List<RoleEntity> validateDeletable(java.util.List<String> ids);

    /** 构造（不落库）新战位实体。 */
    RoleEntity prepareNewRole(RoleFormVO form);

    /** 校验并构造（不落库）修改后的战位实体。 */
    RoleEntity prepareEditedRole(String id, RoleFormVO form);

    // ---------- 关联写（厂商 RoleController:337-411 的四个端点）----------

    /**
     * `POST /v1/role/{roleId}/users`（厂商 `RoleController.usersRelateRole:339-351` +
     * `UserService.usersRelateRole:767-790`）。
     *
     * <ol>
     *   <li>战位不存在 → `role.storage.not.found`（**控制器**先查的，不是 service）；</li>
     *   <li>战位 key 是 `admin` 且存在用户 `admin` → **强制把 admin 用户也塞进列表**；</li>
     *   <li>去重（厂商 `HashSet`）后**先删光该战位的全部用户关联再逐条插**；不存在的 userId 跳过；</li>
     *   <li>每插一条还会把该用户的 `created_at` 覆盖成 now（厂商写错字段，照抄）。</li>
     * </ol>
     *
     * @return 重新查出来的 `RoleUsers`（战位标量 + `list` 全量用户）
     */
    com.csm.paas.core.domain.role.model.entity.RoleUsersEntity relateUsers(String roleId, java.util.List<String> userIds);

    /**
     * `POST /v1/role/{roleId}/suRoles`（厂商 `RoleController.suRolesRelateRole:353-367` +
     * `SubRoleServiceImpl.roleRelateSubRoles:401-424`）。
     *
     * <p>战位不存在 → `role.storage.not.found`；去重后先删光该战位的子角色关联再逐条插，
     * 不存在的 subRoleId 跳过；**并把每个被关联子角色的 `created_at` 覆盖成 now**
     * （与 `POST /v1/role` 的 `subRolesRelateRole` 不同，见 `IRoleRepository`）。
     *
     * @return 战位行 + `subRoleList`
     */
    RoleEntity relateSubRoles(String roleId, java.util.List<String> subRoleIds);

    /**
     * `DELETE /v1/role/{roleId}/{userId}`（厂商 `RoleController.deleteUserInRole:401-411` +
     * `UserService.deleteUserInRole:1368-1392`）。
     *
     * <ol>
     *   <li>战位不存在 → `role.storage.not.found`；</li>
     *   <li>用户是 `admin` 且战位 key 是 `admin` → `user.cannot.remove.ofc.admin`；</li>
     *   <li>该用户**在任一 `login-list:<user>:*` 里出现过**（= ON-LINE）⇒ 删掉匹配的
     *       `gateway-token*` 键与该角色的 `login-list` 键；</li>
     *   <li>删 `g_user_role` 单行。</li>
     * </ol>
     *
     * @return 重新查出来的 `RoleUsers`
     */
    com.csm.paas.core.domain.role.model.entity.RoleUsersEntity removeUserFromRole(String roleId, String userId);

    /**
     * `DELETE /v1/role/{roleId}/app/{appId}`（厂商 `RoleController.deleteRoleApp:158-165` +
     * `RoleService.roleRemoveApp:732-738`）：战位不存在 → `role.storage.not.found`，
     * 否则删 `g_role_software` 单行。**STOMP 推送由 Case 层负责**（厂商在那之后发 `/topic/software`）。
     */
    void removeAppFromRole(String roleId, String appId);
}
