package com.csm.paas.core.domain.user.service;

import com.csm.paas.core.domain.user.model.entity.UserRolesPageEntity;

/**
 * `GET /v1/user/list`（厂商 `UserService.list:836-848` + `getUserRoles:1780-1803`）。
 *
 * <p><b>独立成一个服务</b>：条目里的 `roles` 要走上 `/v1/role` 同一套角色装配（`IRoleQueryService`），
 * 而 `UserQueryServiceImpl` 没有该依赖；塞进去会牵连 4 处既有测试的构造调用（同 `findings/92` 的处置）。
 */
public interface IUserRolesQueryService {

    /**
     * 用户列表（**不过滤 root**，与 `/v1/user/info` 的关键差别）。
     *
     * <p>条目 = 厂商 `UserRoles`（16 键）：`nickname` **恒 null**（厂商没 set）；
     * `roles` 为完整 Role 对象；`subRoles` 空时为 **`[]`**；`admin` = 任一角色 key 为 `admin`。
     * <p>`keyword` 去空格后 `"%" + kw + "%"` 长度 > 38 → 400 `user.invalid.search.keyword.length`。
     */
    UserRolesPageEntity queryUserRolesPage(int page, int size, String keyword);
}
