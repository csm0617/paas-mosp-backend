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

    Optional<RoleEntity> queryByKey(String key);

    Optional<RoleEntity> queryById(String id);

    List<RoleSoftwareEntity> querySoftwareByRoleId(String roleId);

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
}
