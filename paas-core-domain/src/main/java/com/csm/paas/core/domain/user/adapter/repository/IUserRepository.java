package com.csm.paas.core.domain.user.adapter.repository;

import com.csm.paas.core.domain.user.model.entity.UserEntity;

import com.csm.paas.core.domain.user.model.valobj.UserLoginItemVO;

import java.util.List;
import java.util.Optional;

/** 用户仓储（实现放 infrastructure/adapter/repository，只调 dao）。 */
public interface IUserRepository {

    Optional<UserEntity> queryByUsername(String username);

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

    /** 用户关联的子角色名（厂商 {@code SubRoleService.getSubRoleName(userId)}）。 */
    List<String> querySubRoleNamesByUserId(String userId);
}
