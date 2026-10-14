package com.csm.paas.core.domain.role.adapter.repository;

import com.csm.paas.core.domain.role.model.valobj.OnlineUserVO;

import java.util.List;

/**
 * "谁在线"的查询端口（实现在 infrastructure，读 Redis）。
 * <p>为什么单独开一个端口而不是塞进 `IRoleRepository`：数据**不在库里**，在 Redis 的会话列表里。
 * 厂商出处：{@code RoleService.java:207} 的 `redisService.getLoginUser("login-list")`——
 * 扫描 `login-list*` 得到 `login-list:&lt;username&gt;:&lt;roleKey&gt;` 键，再按 `:` 切成 (user, roleKey)。
 */
public interface IRoleOnlineUserRepository {

    /** 当前所有在线会话的 (用户名, 战位 key) 对；无会话时返回空列表。 */
    List<OnlineUserVO> queryOnlineUsers();

    /**
     * 把某用户在某战位上的会话踢掉（厂商 `UserService.deleteUserInRole:1379-1390` 的 ON-LINE 分支）。
     *
     * <p>厂商的"在线"判据**不是键值**，而是"`login-list*` 里存在任意一个 `split(":")[1] == username`
     * 的键"（`getUserLoginStatusList` 只看 `list.contains(name)`）。命中后：
     * <ol>
     *   <li>遍历 `gateway-token*`，把**值等于** `&lt;username&gt;:&lt;roleKey&gt;` 的那个键删掉（只删第一个）；</li>
     *   <li>再删 `login-list:&lt;username&gt;:&lt;roleKey&gt;`。</li>
     * </ol>
     * 不在线则**什么都不做**。
     */
    void evictSessionIfOnline(String username, String roleKey);
}
