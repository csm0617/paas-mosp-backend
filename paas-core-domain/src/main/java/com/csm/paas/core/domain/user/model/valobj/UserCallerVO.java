package com.csm.paas.core.domain.user.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发起写操作的调用者身份。
 * <p>厂商的判定口径（`UserController.getDeleteUser`、`updateUser`）用的是
 * **当前登录用户的完整战位列表里是否有 `admin`**（`RoleList.containAdminRole()`，
 * `controller/response/RoleList.java:17`），不是"当前战位 key 是不是 admin"——
 * 用户可能以非 admin 战位登录但仍是管理员。这里如实建模为布尔。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCallerVO {

    private String username;
    /** 该用户的战位列表里是否存在 key = `admin`。 */
    private boolean adminRole;
}
