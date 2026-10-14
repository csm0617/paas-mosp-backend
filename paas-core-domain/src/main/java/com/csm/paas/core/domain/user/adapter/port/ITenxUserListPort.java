package com.csm.paas.core.domain.user.adapter.port;

import com.csm.paas.core.domain.user.model.entity.TenxUserEntity;

import java.util.List;

/**
 * 天速云"用户列表"端口（厂商 `TenxCloud.getUserList` / `allPageUser`）。
 *
 * <p><b>照抄的怪点（很重要）</b>：厂商 `TenxCloudImpl.getUserList(page, signature):634-639`
 * 是一个**恒空 stub**——
 * <pre>
 *   TenxUserList tenxUserList = new TenxUserList();
 *   tenxUserList.setUsers(new ArrayList&lt;TenxUsers&gt;());
 *   tenxUserList.setListMeta(new ListMeta(0));
 *   return tenxUserList;
 * </pre>
 * 它**不发起任何外部调用、也不抛异常**，`allPageUser` 因此恒返回空列表
 * （`getPage(0) == 0` ⇒ 分页循环不执行）。所以 `GET /v1/user/tenx_user` 在本厂商构建里
 * 的**可观测结果恒为 `data: []`**，`service.get.container.admin.info.failed` 这条 400
 * 在当前厂商实现下**不可达**。
 *
 * <p>我们照抄这个 stub（实现见 `infrastructure/adapter/port/TenxUserListPort`），
 * 不臆造腾讯云 HTTP 调用。
 *
 * <p><b>类 C：本环境不可验证</b>——没有腾讯云对端。
 */
public interface ITenxUserListPort {

    /**
     * 厂商 `TenxCloud.allPageUser(signature):261-273`：分页 100/页拉全部用户。
     * <p>厂商签名里要传 `tenx.open.api.user-list-token`，但 `TenxCloudImpl` 的实现**忽略它**；
     * 这里让实现自己去读配置，域层不接触配置。
     */
    List<TenxUserEntity> allPageUser();
}
