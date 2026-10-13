package com.csm.paas.core.domain.user.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 某战位下的用户列表（厂商 `UserList`：`{userList,total}`）。
 * <p>注意键名是 **`userList`** 而不是 `list`（与 `/v1/user/info` 的 `list` 不同）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserListByRoleEntity {

    private List<UserEntity> userList;
    private long total;
}
