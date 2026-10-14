package com.csm.paas.core.domain.user.adapter.repository;

import com.csm.paas.core.domain.user.model.entity.UserEntity;

import com.csm.paas.core.domain.user.model.valobj.UserLoginItemVO;

import java.util.List;
import java.util.Optional;

/** 用户仓储（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface IUserRepository {

    Optional<UserEntity> queryByUsername(String username);

    Optional<UserEntity> queryById(String id);

    /** 全部用户（厂商 `add` 里用于分配 `uuid`：`userMapper.select(allRows)`）。 */
    List<UserEntity> queryAll();

    /** 登录页账号下拉用（厂商 GET /v1/user/get，**公开**）。 */
    List<UserLoginItemVO> queryLoginList();

    long count();

    /**
     * 用户管理列表按用户名模糊分页（厂商 {@code UserService.getUserList} 的取数部分：
     * {@code username LIKE %} + {@code ORDER BY created_at DESC}）。
     *
     * @param likeKeyword 已带 {@code %} 的模糊串；null 表示不过滤
     */
    List<UserEntity> queryPageByKeyword(String likeKeyword, int offset, int size);

    /** 同上条件的总数（厂商用 PageHelper 的 {@code Page.getTotal()}）。 */
    long countByKeyword(String likeKeyword);

    /**
     * 用户名关联的战位/角色名（厂商 {@code RoleService.getUserRoleNameByType(username, type)}）：
     * {@code g_user_role} 连 {@code g_role}，按 {@code g_role.type} 过滤，按 {@code g_role.created_at} 倒序。
     *
     * @param type 1=战位 2=角色
     */
    List<String> queryRoleNamesByUsernameAndType(String username, int type);

    /**
     * 某战位下的用户，分页（厂商 `UserService.queryUsersByRoleId`，`:863-875`）。
     * <p>注意厂商**列表里过滤掉 `root`，但 `total` 取分页总数（含 root）**；调用方负责过滤。
     */
    List<UserEntity> queryPageByRoleId(String roleId, int offset, int size);

    /** 某战位下的用户总数（含 root）。 */
    long countByRoleId(String roleId);

    /** 用户关联的子角色名（厂商 {@code SubRoleService.getSubRoleName(userId)}）。 */
    List<String> querySubRoleNamesByUserId(String userId);

    /** 只更新头像（厂商 `UserService.updateAvatar:1589-1591` 的 `updateByPrimaryKeySelective`）。 */
    void updateAvatar(String userId, String avatar);

    /** 用户关联的战位 key 列表（判定"是否有 admin 战位"，厂商 `RoleList.containAdminRole()`）。 */
    List<String> queryRoleKeysByUsername(String username);

    /**
     * 删除用户及其全部关联（`g_user_role` + `g_user_sub_role` + `g_user`），**一个事务**。
     * <p>厂商出处：`UserService.getDeleteUser`（`UserService.java:829-834`）——先删两张关系表再删用户行；
     * 该方法的调用方 `deleteUser` 打了 `@Transactional`（`:820`）。
     */
    void deleteUserCascade(String userId);

    // ---------- 新增（厂商 UserService.add，UserService.java:450-545）----------

    void insert(UserEntity user);

    /**
     * 更新用户（厂商 `UserService.update` 末尾的 `updateByPrimaryKeySelective`，`UserService.java:726`）。
     * <p>只更新厂商会动的那些列：`username/password/status/nickname/desc/avatar/email/birth/phone/created_at/updated_at`
     * ——`uuid`/`api_token`/`tenx_username`/`last_login_role` 在厂商那边是 null 所以不会被写。
     */
    void update(UserEntity user);

    /** 按 key 批量取战位/角色 id（厂商在 `add`/`update` 里逐个 select by key，这里合成一条）。 */
    List<String> queryRoleIdsByKeys(List<String> keys);

    /**
     * 按**类型**替换用户关联（厂商 `UserService.rolesRelate2User`，`:794-806`）：
     * 先删该用户"该类型"的关联，再插入给定 roleId（不存在的 roleId 跳过）。
     *
     * @param type 1=战位 2=角色
     */
    void replaceUserRoleRelations(String userId, int type, List<String> roleIds);

    /**
     * **替换用户的全部**战位/角色关联（不分类型）——厂商 `UserService.UserRelationRole`
     * （`UserService.java:1869-1892`，`POST /v1/user/userToRole`）：
     * 先删该用户**所有** `g_user_role`，再按去重后的 roleId 逐条插入。
     * <p>与 {@link #replaceUserRoleRelations} 的区别：那个只动**指定类型**的关联。
     */
    void replaceAllUserRoleRelations(String userId, List<String> roleIds);

    /**
     * 替换用户的子角色关联（厂商 `SubRoleService.userAddRole`，`SubRoleServiceImpl.java:347-359`）：
     * 先删该用户全部 `g_user_sub_role`，再逐条插入（id 用随机 UUID）。
     */
    void replaceUserSubRoleRelations(String userId, List<String> subRoleIds);

    // ---------- 用户导入（厂商 UserService.importExcel / existUpdateUser，UserService.java:1181-1351）----------

    /**
     * 按**用户名**删光该用户的 `g_user_role`（厂商 `UserMapper.deleteExistUserRelationRole`，
     * `dao/UserMapper.java:138` 的 `@Delete`）。与按 userId 删等价，但导入的覆盖分支就是按用户名删的，照抄。
     */
    void deleteUserRoleRelationsByUsername(String username);

    /** 删光某用户的 `g_user_sub_role`（厂商 `UserSubRoleMapper.deleteRelateUserToSubRole`）。 */
    void deleteUserSubRoleRelationsByUserId(String userId);

    /**
     * 插**一条**角色关系（厂商在导入里是逐条 `userRoleMapper.insertSelective`，
     * **不去重**：col3 里同一个 key 出现两次就会插两行）。
     */
    void insertUserRoleRelation(String userId, String roleId, long createdAt);

    /**
     * 插**一条**子角色关系（厂商 `insertSelective`：`id` 用随机 UUID、`updated_at` **不写**）。
     */
    void insertUserSubRoleRelation(String id, String userId, String subRoleId, long createdAt);
}
