package com.csm.paas.core.domain.user.service;

/**
 * 用户访问权限判定（厂商 `GET /v1/user/access_permission?path=` → `UserService.accessPermission:1409-1455`）。
 *
 * <p><b>独立成一个服务的原因</b>：这段逻辑需要 `IRoleRepository` + `ISoftwareRepository` + `IRoleQueryService`
 * 三者，而 `UserQueryServiceImpl` 缺 `ISoftwareRepository`；塞进去要改 4 处既有测试的构造调用。
 */
public interface IUserAccessService {

    /**
     * 指定路径是否有权限。
     *
     * <ol>
     *   <li>取该用户战位列表里**最后一个**战位的 `id`/`key`（厂商 for 循环后覆盖前）；</li>
     *   <li>`roleId != null || "admin".equals(roleName)` → 该战位关联的每个软件：取软件 `key` 与其名下
     *       所有服务路径，拼成 `"/api/" + key + "/" + pathName`；</li>
     *   <li>`findPath`：三个**照抄的正则**；admin 且任一命中 → true；路径空或 `/` → true；与允许列表**全等** → true；否则 false。</li>
     * </ol>
     */
    boolean checkAccessPermission(String username, String path);
}
