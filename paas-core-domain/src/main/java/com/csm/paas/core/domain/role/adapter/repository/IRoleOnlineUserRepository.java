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
}
