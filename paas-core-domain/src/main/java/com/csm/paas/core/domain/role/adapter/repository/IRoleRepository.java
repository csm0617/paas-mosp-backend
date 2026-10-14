package com.csm.paas.core.domain.role.adapter.repository;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.role.model.entity.RoleSoftwareEntity;
import com.csm.paas.core.domain.role.model.valobj.RoleUserVO;

import java.util.List;
import java.util.Optional;

/** 战位的本地仓储接口（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface IRoleRepository {

    List<RoleEntity> queryOrderByCreatedAtDesc();

    /** 全量按 `created_at` 倒序分页（厂商 `RoleService.java:366`）。 */
    List<RoleEntity> queryPage(int offset, int size);

    long count();

    /** 某用户关联的战位（厂商 `RoleService.java:369` 的三表 join；该分支**不分页**）。 */
    List<RoleEntity> queryByUsername(String username);

    /**
     * 某用户关联的、指定类型的战位/角色（厂商 `RoleService.listByRoleType`，`:321-331`）。
     * <p>用于 `GET /v1/user/detailByUserId` 的 `roles`(type=1) 与 `belongRole`(type=2)。
     */
    List<RoleEntity> queryByUsernameAndType(String username, int type);

    /**
     * 过滤出真实存在的战位/角色 id——厂商 `rolesRelate2User`（`UserService.java:798`）对每个 roleId
     * 先 `selectByPrimaryKey`，不存在就 `continue`。
     */
    List<String> filterExistingIds(List<String> ids);

    Optional<RoleEntity> queryByKey(String key);

    Optional<RoleEntity> queryById(String id);

    List<RoleSoftwareEntity> querySoftwareByRoleId(String roleId);

    /** 分页版（厂商 `roleSoftwareList` 用 `PageHelper.startPage` + `order by order`）。 */
    List<RoleSoftwareEntity> querySoftwarePage(String roleId, int offset, int size);

    long countSoftwareByRoleId(String roleId);

    /** 按 `g_role.type` 分页、`created_at` 倒序（厂商 `RoleService.java:196`）。 */
    List<RoleEntity> queryPageByType(int type, int offset, int size);

    long countByType(int type);

    /** 按 `g_role.type` + `name LIKE`（厂商 `:200`；该分支**不分页**）。 */
    List<RoleEntity> queryByTypeAndNameLike(int type, String likeKeyword);

    /** 仅按 `name LIKE`、**不限 type**（厂商 `RoleService.java:278`，`/v1/role/list` 的分支）。 */
    List<RoleEntity> queryByNameLike(String likeKeyword);

    /** 战位关联用户（厂商 `getRoleUser`：`g_user_role` 连 `g_user`）。 */
    List<RoleUserVO> queryUsersByRoleId(String roleId);

    /** 战位关联的子角色 id（厂商 `getSubRoleListByRoleId`：`g_sub_role_role` 连 `g_sub_role`）。 */
    List<String> querySubRoleIdsByRoleId(String roleId);

    // ---------- 删除（厂商 `RoleService.deleteRoleRelation`，RoleService.java:707-730）----------
    // 厂商的 6 张表删除**顺序固定**，且全部包在一个 @Transactional 里；顺序与事务边界由 Case 层编排，
    // 这里只提供单表删除动作（不含业务判断）。

    void deleteSubRoleRelationsByRoleId(String roleId);

    void deleteUserAuditByRoleId(String roleId);

    void deleteUserRoleByRoleId(String roleId);

    void deleteUserLoginByRoleId(String roleId);

    void deleteRoleSoftwareByRoleId(String roleId);

    void deleteById(String roleId);

    // ---------- 写（新增 / 修改，厂商 RoleService.addRole / editRole）----------

    /** key **或** name 已存在（厂商 `existKeyName`，`RoleService.java:659-661`，注意是 **OR**）。 */
    boolean existsByKeyOrName(String key, String name);

    /** 除自己之外是否已存在该 key（厂商 `duplicateKey`）。 */
    boolean existsByKeyExcludingId(String key, String id);

    /** 除自己之外是否已存在该 name（厂商 `duplicateName`）。 */
    boolean existsByNameExcludingId(String name, String id);

    /** name 是否存在（厂商 `existRoleName`，`GET /v1/role/exist_role_name`）。 */
    boolean existsByName(String name);

    /** 按 key 批量取战位 id，**按入参顺序**返回、不存在的 key 跳过（厂商逐个 select by key 的等价写法）。 */
    List<String> queryRoleIdsByKeys(List<String> keys);

    void insert(RoleEntity role);

    /** 更新战位行（厂商 `editRole` 的 `updateByPrimaryKeySelective`）。 */
    void update(RoleEntity role);

    /** 用给定用户 id 列表**替换**该战位的用户关联（厂商 `usersRelateRole`）。 */
    void replaceUserRelationsByRoleId(String roleId, List<String> userIds);

    /** 用给定子角色 id 列表**替换**该战位的子角色关联（厂商 `subRolesRelateRole`）。 */
    void replaceSubRoleRelationsByRoleId(String roleId, List<String> subRoleIds);
}
