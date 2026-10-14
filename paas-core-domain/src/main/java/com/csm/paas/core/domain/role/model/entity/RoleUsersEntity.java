package com.csm.paas.core.domain.role.model.entity;

import com.csm.paas.core.domain.user.model.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `RoleUsers`（`RoleUsers extends Role` + `List<User> list`）：
 * `POST /v1/role/{roleId}/users` 与 `DELETE /v1/role/{roleId}/{userId}` 的响应体。
 *
 * <p>与 {@link RoleEntity} 的区别是**只多一个 `list`**（完整 `User`，含 `password` 等 14 列），
 * 而 `RoleEntity.userList` 是 `{userId,userName}` 的 `RoleUser`。两者**不是同一个东西**，别混用。
 * <p>注意：厂商 `BeanUtils.copyProperties(role, roleUsers)` 只搬标量，所以
 * `onLineUserList`/`softwareList`/`userList`/`subRoleList` 四个键**恒为 null**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleUsersEntity {

    private RoleEntity role;
    /** 该战位下的完整用户实体（不过滤 root、不排序）。 */
    private List<UserEntity> list;
}
