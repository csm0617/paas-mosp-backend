package com.csm.paas.core.domain.user.service;

import com.csm.paas.core.domain.user.model.entity.TenxUserEntity;

import java.util.List;

/**
 * `GET /v1/user/tenx_user` 的查询（厂商 `UserService.getTenxUserList:1584-1618`）。
 */
public interface ITenxUserQueryService {

    /**
     * 天速云用户列表（含 `apiToken`）。
     *
     * <p>规则照抄厂商：
     * <ol>
     *   <li>拉取异常 ⇒ 400 `service.get.container.admin.info.failed`；</li>
     *   <li>`users == null || users.isEmpty()` ⇒ 返回**空列表**（不是 null）；</li>
     *   <li>`userName == null` ⇒ 返回全量（映射成 `{userName, apiToken}`）；</li>
     *   <li>`userName != null` ⇒ **精确等值**过滤，只返回相等项（无匹配 ⇒ 空列表）。</li>
     * </ol>
     */
    List<TenxUserEntity> queryTenxUserList(String userName);
}
