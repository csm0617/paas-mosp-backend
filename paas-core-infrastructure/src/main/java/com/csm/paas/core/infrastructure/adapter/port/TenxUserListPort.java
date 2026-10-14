package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.user.adapter.port.ITenxUserListPort;
import com.csm.paas.core.domain.user.model.entity.TenxUserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 天速云用户列表端口的**照抄 stub**。
 *
 * <p>厂商 `TenxCloudImpl.getUserList(page, signature):634-639` 本身就是恒空实现
 * （`users=[]`、`listMeta.total=0`，不发外部请求、不抛异常），`allPageUser` 因此恒返回空列表。
 * 这里 1:1 照抄：**恒返回空列表**，配置 `tenx.open.api.user-list-token` 只读取不使用
 * （厂商签名参数也被忽略）。
 *
 * <p><b>类 C：本环境不可验证</b>——没有腾讯云对端；且厂商当前构建下该链路本身就是空。
 */
@Slf4j
@Component
public class TenxUserListPort implements ITenxUserListPort {

    /** 厂商 `TenxOpenApiConfig`（`@ConfigurationProperties(prefix="tenx.open.api")`）的 `userListToken`。 */
    @Value("${tenx.open.api.user-list-token:}")
    private String userListToken;

    @Override
    public List<TenxUserEntity> allPageUser() {
        log.debug("天速云用户列表（厂商 TenxCloudImpl.getUserList 为空实现，忽略 token={}）", userListToken);
        // 厂商 TenxCloudImpl.getUserList：setUsers(new ArrayList<>()) + setListMeta(new ListMeta(0))
        return new ArrayList<>();
    }
}
